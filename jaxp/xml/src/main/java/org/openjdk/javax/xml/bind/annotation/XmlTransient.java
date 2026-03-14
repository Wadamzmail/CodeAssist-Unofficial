package org.openjdk.javax.xml.bind.annotation;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Prevents the mapping of a JavaBean property/type to XML representation.
 *
 * <p>The {@code @XmlTransient} annotation is useful for resolving name collisions between a
 * JavaBean property name and a field name or preventing the mapping of a field/property. A name
 * collision can occur when the decapitalized JavaBean property name and a field name are the same.
 * If the JavaBean property refers to the field, then the name collision can be resolved by
 * preventing the mapping of either the field or the JavaBean property using the
 * {@code @XmlTransient} annotation.
 *
 * <p>When placed on a class, it indicates that the class shouldn't be mapped to XML by itself.
 * Properties on such class will be mapped to XML along with its derived classes, as if the class is
 * inlined.
 *
 * <p><b>Usage</b>
 *
 * <p>The {@code @XmlTransient} annotation can be used with the following program elements:
 *
 * <ul>
 *   <li>a JavaBean property
 *   <li>field
 *   <li>class
 * </ul>
 *
 * <p>{@code @XmlTransient} is mutually exclusive with all other JAXB defined annotations.
 *
 * <p>See "Package Specification" in javax.xml.bind.package javadoc for additional common
 * information.
 *
 * <p><b>Example:</b> Resolve name collision between JavaBean property and field name
 *
 * <pre>
 *   // Example: Code fragment
 *   public class USAddress {
 *
 *       // The field name "name" collides with the property name
 *       // obtained by bean decapitalization of getName() below
 *       &#64;XmlTransient public String name;
 *
 *       String getName() {..};
 *       String setName() {..};
 *   }
 *
 * {@code
 *
 *   <!-- Example: XML Schema fragment -->
 *   <xs:complexType name="USAddress">
 *     <xs:sequence>
 *       <xs:element name="name" type="xs:string"/>
 *     </xs:sequence>
 *   </xs:complexType>
 * }</pre>
 *
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @since 1.6, JAXB 2.0
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD, TYPE})
public @interface XmlTransient {}
