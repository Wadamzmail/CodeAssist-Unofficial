package com.tyron.resolver.parser;

import com.tyron.resolver.model.Dependency;
import com.tyron.resolver.model.Pom;
import com.tyron.resolver.repository.RepositoryManager;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParserException;

public class PomParser {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{(.*?)\\}");

    private Pom parent;
    private final Map<String, String> mProperties;
    private final RepositoryManager repository;

    public PomParser(RepositoryManager repository) {
        this.repository = repository;
        mProperties = new HashMap<>();
    }

    public Pom parse(File in) throws IOException, XmlPullParserException, SAXException {
        return parse(FileUtils.readFileToString(in, StandardCharsets.UTF_8));
    }

    public Pom parse(String in) throws IOException, XmlPullParserException, SAXException {
        if (in == null) {
            return null;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder;
        try {
            documentBuilder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new Error(e);
        }
        InputSource source = new InputSource(new StringReader(in));
        Document document = documentBuilder.parse(source);
        Element documentElement = document.getDocumentElement();
        if (!"project".equals(documentElement.getTagName())) {
            return null;
        }

        return parseProject(documentElement);
    }

    private Pom parseProject(Element projectElement) {
        Pom pom = new Pom();
        NodeList childNodes = projectElement.getChildNodes();

        // المرحلة 1: استخراج الـ Parent أولاً لوراثة الخصائص
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if ("parent".equals(child.getNodeName())) {
                parent = parseParent((Element) child);
                pom.setParent(parent);
                // إذا كان للأب خصائص، أضفها كقيم افتراضية
                if (parent != null && parent.getProperties() != null) {
                    // ملاحظة: سنقوم بالكتابة فوقها لاحقاً بالخصائص المحلية إذا وجدت
                    for (Map.Entry<String, String> entry : parent.getProperties().entrySet()) {
                        mProperties.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
                break;
            }
        }

        // المرحلة 2: استخراج البيانات الأساسية والخصائص المحلية (Properties)
        // يجب فعل هذا قبل قراءة الـ dependencies لحل المتغيرات
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            String nodeName = child.getNodeName();

            if ("properties".equals(nodeName)) {
                mProperties.putAll(parseProperties((Element) child));
            } else if ("groupId".equals(nodeName)) {
                String val = getTextContent(child);
                pom.setGroupId(val);
                mProperties.put("project.groupId", val);
            } else if ("artifactId".equals(nodeName)) {
                String val = getTextContent(child);
                pom.setArtifactId(val);
                mProperties.put("project.artifactId", val);
            } else if ("version".equals(nodeName)) {
                String val = getTextContent(child);
                pom.setVersionName(val);
                mProperties.put("project.version", val);
            } else if ("packaging".equals(nodeName)) {
                pom.setPackaging(getTextContent(child));
            }
        }

        // إذا لم يتم تحديد Version، قد يتم وراثته من الـ Parent
        if (pom.getVersionName() == null && parent != null) {
            pom.setVersionName(parent.getVersionName());
            mProperties.put("project.version", parent.getVersionName());
        }
        if (pom.getGroupId() == null && parent != null) {
            pom.setGroupId(parent.getGroupId());
            mProperties.put("project.groupId", parent.getGroupId());
        }

        // حفظ الخصائص النهائية في كائن الـ Pom
        pom.setProperties(new HashMap<>(mProperties));

        // المرحلة 3: الآن يمكننا قراءة الـ Dependencies بأمان
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            String nodeName = child.getNodeName();

            if ("dependencies".equals(nodeName)) {
                pom.setDependencies(parseDependencies((Element) child));
            } else if ("dependencyManagement".equals(nodeName)) {
                List<Dependency> dependencies = parseDependencies((Element) child);
                pom.setManagedDependencies(dependencies);
            }
        }

        return pom;
    }

    private String getTextContent(Node child) {
        String value = child.getTextContent();
        if (value == null) return "";
        
        // دعم الاستبدال المتكرر للمتغيرات (اختياري، لكن مفيد)
        String previousValue;
        int loopCount = 0;
        do {
            previousValue = value;
            Matcher matcher = VARIABLE_PATTERN.matcher(value);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String name = matcher.group(1);
                String property = mProperties.get(name);
                
                // البحث في Parent إذا لم نجدها محلياً
                if (property == null && parent != null) {
                    property = parent.getProperty(name);
                }
                
                // Fallback: البحث في خصائص النظام (System Properties)
                if (property == null) {
                    property = System.getProperty(name);
                }

                if (property != null) {
                    // Escape $ and \ because they have special meaning in appendReplacement
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(property));
                }
            }
            matcher.appendTail(sb);
            value = sb.toString();
            loopCount++;
        } while (!value.equals(previousValue) && loopCount < 5); // تحديد عدد التكرار لتجنب Infinite Loop

        return value.trim();
    }

    private Pom parseParent(Element element) {
        Dependency dependency = new Dependency();
        
        NodeList groupIdList = element.getElementsByTagName("groupId");
        if (groupIdList.getLength() > 0) {
            dependency.setGroupId(groupIdList.item(0).getTextContent());
        }

        NodeList artifactIdList = element.getElementsByTagName("artifactId");
        if (artifactIdList.getLength() > 0) {
            dependency.setArtifactId(artifactIdList.item(0).getTextContent());
        }

        NodeList versionList = element.getElementsByTagName("version");
        if (versionList.getLength() > 0) {
            dependency.setVersionName(versionList.item(0).getTextContent());
        }
        
        // محاولة جلب الـ Pom الخاص بالأب من الـ Repository
        // ملاحظة: يجب أن يكون الكود قادراً على التعامل مع null إذا لم يكن الملف موجوداً محلياً
        try {
            return repository.getPom(dependency.toString());
        } catch (Exception e) {
            // يمكن تسجيل الخطأ هنا، أو تجاهله إذا كنا لا نملك الأب
            return null; 
        }
    }

    private Map<String, String> parseProperties(Element propertyElement) {
        Map<String, String> properties = new HashMap<>();
        NodeList propertyTags = propertyElement.getChildNodes();
        for (int i = 0; i < propertyTags.getLength(); i++) {
            if (propertyTags.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element property = (Element) propertyTags.item(i);
            String key = property.getTagName();
            String value = property.getTextContent();
            properties.put(key, value);
        }
        return properties;
    }

    private List<Dependency> parseDependencies(Element dependenciesNode) {
        List<Dependency> dependencies = new ArrayList<>();
        NodeList dependencyList = dependenciesNode.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyList.getLength(); i++) {
            Element dependencyElement = (Element) dependencyList.item(i);

            Dependency dependency = new Dependency();
            
            // GroupId
            NodeList groupIdList = dependencyElement.getElementsByTagName("groupId");
            if (groupIdList.getLength() > 0) {
                dependency.setGroupId(getTextContent(groupIdList.item(0)));
            }

            // ArtifactId
            NodeList artifactIdList = dependencyElement.getElementsByTagName("artifactId");
            if (artifactIdList.getLength() > 0) {
                dependency.setArtifactId(getTextContent(artifactIdList.item(0)));
            }

            // Scope
            NodeList scopeList = dependencyElement.getElementsByTagName("scope");
            if (scopeList.getLength() > 0) {
                dependency.setScope(getTextContent(scopeList.item(0)));
            }

            // Version
            NodeList versionList = dependencyElement.getElementsByTagName("version");
            if (versionList.getLength() > 0) {
                dependency.setVersionName(getTextContent(versionList.item(0)));
            } else {
                // محاولة حل الإصدار المفقود عبر الـ Managed Dependencies (في الأب)
                resolveManagedVersion(dependency);
            }

            // Exclusions
            NodeList exclusion = dependencyElement.getElementsByTagName("exclusions");
            if (exclusion.getLength() > 0) {
                parseExclusions((Element) exclusion.item(0), dependency);
            }

            if (dependency.getGroupId() != null && dependency.getArtifactId() != null) {
                dependencies.add(dependency);
            }
        }
        return dependencies;
    }

    private void resolveManagedVersion(Dependency dependency) {
        Pom current = parent;
        while (current != null) {
            List<Dependency> managedDependencies = current.getManagedDependencies();
            if (managedDependencies != null) {
                for (Dependency managedDependency : managedDependencies) {
                    if (managedDependency.getGroupId().equals(dependency.getGroupId()) &&
                        managedDependency.getArtifactId().equals(dependency.getArtifactId())) {
                        
                        dependency.setVersionName(managedDependency.getVersionName());
                        if (dependency.getScope() == null) {
                            dependency.setScope(managedDependency.getScope());
                        }
                        return;
                    }
                }
            }
            current = current.getParent();
        }
    }

    private void parseExclusions(Element exclusionsElement, Dependency dependency) {
        NodeList exclusionElementList = exclusionsElement.getElementsByTagName("exclusion");
        for (int j = 0; j < exclusionElementList.getLength(); j++) {
            Element exclusionElement = (Element) exclusionElementList.item(j);
            Dependency exclusionDependency = new Dependency();

            NodeList groupId = exclusionElement.getElementsByTagName("groupId");
            if (groupId.getLength() > 0) {
                exclusionDependency.setGroupId(getTextContent(groupId.item(0)));
            }

            NodeList artifactId = exclusionElement.getElementsByTagName("artifactId");
            if (artifactId.getLength() > 0) {
                exclusionDependency.setArtifactId(getTextContent(artifactId.item(0)));
            }
            
            dependency.addExclude(exclusionDependency);
        }
    }
}
