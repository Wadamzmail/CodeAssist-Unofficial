// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.com.intellij.core;

import org.jetbrains.kotlin.com.intellij.DynamicBundle;
import org.jetbrains.kotlin.com.intellij.codeInsight.folding.CodeFoldingSettings;
import org.jetbrains.kotlin.com.intellij.concurrency.JobLauncher;
import org.jetbrains.kotlin.com.intellij.ide.plugins.DisabledPluginsState;
import org.jetbrains.kotlin.com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import org.jetbrains.kotlin.com.intellij.ide.plugins.PluginDescriptorLoader;
import org.jetbrains.kotlin.com.intellij.ide.plugins.PluginManagerCore;
import org.jetbrains.kotlin.com.intellij.lang.DefaultASTFactory;
import org.jetbrains.kotlin.com.intellij.lang.DefaultASTFactoryImpl;
import org.jetbrains.kotlin.com.intellij.lang.Language;
import org.jetbrains.kotlin.com.intellij.lang.LanguageExtension;
import org.jetbrains.kotlin.com.intellij.lang.LanguageParserDefinitions;
import org.jetbrains.kotlin.com.intellij.lang.ParserDefinition;
import org.jetbrains.kotlin.com.intellij.lang.PsiBuilderFactory;
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderFactoryImpl;
import org.jetbrains.kotlin.com.intellij.mock.MockApplication;
import org.jetbrains.kotlin.com.intellij.mock.MockFileDocumentManagerImpl;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationInfo;
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.kotlin.com.intellij.openapi.application.impl.ApplicationInfoImpl;
import org.jetbrains.kotlin.com.intellij.openapi.command.CommandProcessor;
import org.jetbrains.kotlin.com.intellij.openapi.command.impl.CoreCommandProcessor;
import org.jetbrains.kotlin.com.intellij.openapi.editor.impl.DocumentImpl;
import org.jetbrains.kotlin.com.intellij.openapi.extensions.ExtensionPoint;
import org.jetbrains.kotlin.com.intellij.openapi.extensions.ExtensionPointDescriptor;
import org.jetbrains.kotlin.com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.kotlin.com.intellij.openapi.extensions.Extensions;
import org.jetbrains.kotlin.com.intellij.openapi.extensions.ExtensionsArea;
import org.jetbrains.kotlin.com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import org.jetbrains.kotlin.com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.kotlin.com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.kotlin.com.intellij.openapi.fileTypes.FileTypeExtension;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.kotlin.com.intellij.openapi.progress.impl.CoreProgressManager;
import org.jetbrains.kotlin.com.intellij.openapi.util.ClassExtension;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManagerListener;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.impl.CoreVirtualFilePointerManager;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jetbrains.kotlin.com.intellij.psi.PsiReferenceService;
import org.jetbrains.kotlin.com.intellij.psi.PsiReferenceServiceImpl;
import org.jetbrains.kotlin.com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.kotlin.com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistryImpl;
import org.jetbrains.kotlin.com.intellij.psi.stubs.CoreStubTreeLoader;
import org.jetbrains.kotlin.com.intellij.psi.stubs.StubTreeLoader;
import org.jetbrains.kotlin.com.intellij.util.KeyedLazyInstanceEP;
import org.jetbrains.kotlin.com.intellij.util.graph.GraphAlgorithms;
import org.jetbrains.kotlin.com.intellij.util.graph.impl.GraphAlgorithmsImpl;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.org.picocontainer.MutablePicoContainer;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;


public class CoreApplicationEnvironment {
    @NotNull
    protected final VirtualFileSystem myJarFileSystem;
    protected final MockApplication myApplication;
    private final CoreFileTypeRegistry myFileTypeRegistry;
    private final CoreLocalFileSystem myLocalFileSystem;
    private final VirtualFileSystem myJrtFileSystem;
    @NotNull
    private final Disposable myParentDisposable;
    private final boolean myUnitTestMode;

    public CoreApplicationEnvironment(@NotNull Disposable parentDisposable) {
        this(parentDisposable, true);
    }

    public CoreApplicationEnvironment(@NotNull Disposable parentDisposable, boolean unitTestMode) {
        myParentDisposable = parentDisposable;
        myUnitTestMode = unitTestMode;

        DisabledPluginsState.setIgnoreDisabledPlugins(true);

        myFileTypeRegistry = new CoreFileTypeRegistry();

        myApplication = createApplication(myParentDisposable);
        ApplicationManager.setApplication(myApplication,
                () -> myFileTypeRegistry,
                myParentDisposable);
        myLocalFileSystem = createLocalFileSystem();
        myJarFileSystem = createJarFileSystem();
        myJrtFileSystem = createJrtFileSystem();

        registerApplicationService(FileDocumentManager.class, new MockFileDocumentManagerImpl(null, DocumentImpl::new));

        registerApplicationExtensionPoint(new ExtensionPointName<>("com.intellij.virtualFileManagerListener"), VirtualFileManagerListener.class);
        List<VirtualFileSystem> fs = myJrtFileSystem != null
                ? Arrays.asList(myLocalFileSystem, myJarFileSystem, myJrtFileSystem)
                : Arrays.asList(myLocalFileSystem, myJarFileSystem);
        registerApplicationService(VirtualFileManager.class, new VirtualFileManagerImpl(fs));

        //fake EP for cleaning resources after area disposing (otherwise KeyedExtensionCollector listener will be copied to the next area)
        registerApplicationExtensionPoint(new ExtensionPointName<>("com.intellij.virtualFileSystem"), KeyedLazyInstanceEP.class);

        registerApplicationService(EncodingManager.class, new CoreEncodingRegistry());
        registerApplicationService(VirtualFilePointerManager.class, createVirtualFilePointerManager());
        registerApplicationService(DefaultASTFactory.class, new DefaultASTFactoryImpl());
        registerApplicationService(PsiBuilderFactory.class, new PsiBuilderFactoryImpl());
        registerApplicationService(ReferenceProvidersRegistry.class, new ReferenceProvidersRegistryImpl());
        registerApplicationService(StubTreeLoader.class, new CoreStubTreeLoader());
        registerApplicationService(PsiReferenceService.class, new PsiReferenceServiceImpl());
        registerApplicationService(ProgressManager.class, createProgressIndicatorProvider());
        registerApplicationService(JobLauncher.class, createJobLauncher());
        registerApplicationService(CodeFoldingSettings.class, new CodeFoldingSettings());
        registerApplicationService(CommandProcessor.class, new CoreCommandProcessor());
        registerApplicationService(GraphAlgorithms.class, new GraphAlgorithmsImpl());

        myApplication.registerService(ApplicationInfo.class, ApplicationInfoImpl.class);

        registerApplicationExtensionPoint(DynamicBundle.LanguageBundleEP.EP_NAME, DynamicBundle.LanguageBundleEP.class);
    }

    public static <T> void registerComponentInstance(@NotNull MutablePicoContainer container, @NotNull Class<T> key, @NotNull T implementation) {
        container.unregisterComponent(key);
        container.registerComponentInstance(key, implementation);
    }

    public static <T> void registerExtensionPoint(@NotNull ExtensionsArea area,
                                                  @NotNull ExtensionPointName<T> extensionPointName,
                                                  @NotNull Class<? extends T> aClass) {
        registerExtensionPoint(area, extensionPointName.getName(), aClass);
    }

    public static <T> void registerExtensionPoint(@NotNull ExtensionsArea area, @NotNull String name, @NotNull Class<? extends T> aClass) {
        if (!area.hasExtensionPoint(name)) {
            ExtensionPoint.Kind kind = aClass.isInterface() || Modifier.isAbstract(aClass.getModifiers()) ? ExtensionPoint.Kind.INTERFACE : ExtensionPoint.Kind.BEAN_CLASS;
            area.registerExtensionPoint(name, aClass.getName(), kind);
        }
    }

    public static <T> void registerApplicationExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<? extends T> aClass) {
        registerExtensionPoint(Extensions.getRootArea(), extensionPointName.getName(), aClass);
    }

    public static <T> void registerApplicationDynamicExtensionPoint(@NotNull String extensionPointName, @NotNull Class<? extends T> aClass) {
        registerExtensionPoint(Extensions.getRootArea(), extensionPointName, aClass);
    }

    public static void registerExtensionPointAndExtensions(@NotNull Path pluginRoot, @NotNull String fileName, @NotNull ExtensionsArea area) {
        IdeaPluginDescriptorImpl descriptor = PluginDescriptorLoader.loadForCoreEnv(pluginRoot, fileName);
        if (descriptor == null) {
            PluginManagerCore.getLogger().error("Cannot load " + fileName + " from " + pluginRoot);
            return;
        }

        List<ExtensionPointDescriptor> extensionPoints = descriptor.appContainerDescriptor.extensionPoints;
        ExtensionsAreaImpl areaImpl = (ExtensionsAreaImpl) area;
        if (extensionPoints != null) {
            areaImpl.registerExtensionPoints(extensionPoints, descriptor);
        }
        descriptor.registerExtensions(areaImpl.extensionPoints, descriptor.appContainerDescriptor, null);
    }

    public <T> void registerApplicationService(@NotNull Class<T> serviceInterface, @NotNull T serviceImplementation) {
        myApplication.registerService(serviceInterface, serviceImplementation);
    }

    @Nullable
    protected VirtualFileSystem createJrtFileSystem() {
        return null;
    }

    @NotNull
    protected VirtualFilePointerManager createVirtualFilePointerManager() {
        return new CoreVirtualFilePointerManager();
    }

    @NotNull
    protected MockApplication createApplication(@NotNull Disposable parentDisposable) {
        return new MockApplication(parentDisposable) {
            @Override
            public boolean isUnitTestMode() {
                return myUnitTestMode;
            }
        };
    }

    @NotNull
    protected JobLauncher createJobLauncher() {
        return new JobLauncher() {
            // no-op
        };
    }

    @NotNull
    protected ProgressManager createProgressIndicatorProvider() {
        return new CoreProgressManager();
    }

    @NotNull
    protected VirtualFileSystem createJarFileSystem() {
        return new CoreJarFileSystem();
    }

    @NotNull
    protected CoreLocalFileSystem createLocalFileSystem() {
        return new CoreLocalFileSystem();
    }

    @NotNull
    public MockApplication getApplication() {
        return myApplication;
    }

    @NotNull
    public Disposable getParentDisposable() {
        return myParentDisposable;
    }

    public <T> void registerApplicationComponent(@NotNull Class<T> interfaceClass, @NotNull T implementation) {
        registerComponentInstance(myApplication.getPicoContainer(), interfaceClass, implementation);
        if (implementation instanceof Disposable) {
            Disposer.register(myApplication, (Disposable) implementation);
        }
    }

    public void registerFileType(@NotNull FileType fileType, @NotNull @NonNls String extension) {
        myFileTypeRegistry.registerFileType(fileType, extension);
    }

    public void registerParserDefinition(@NotNull ParserDefinition definition) {
        addExplicitExtension(LanguageParserDefinitions.INSTANCE, definition.getFileNodeType().getLanguage(), definition);
    }

    public <T> void addExplicitExtension(@NotNull LanguageExtension<T> instance, @NotNull Language language, @NotNull T object) {
        instance.addExplicitExtension(language, object, myParentDisposable);
    }

    public void registerParserDefinition(@NotNull Language language, @NotNull ParserDefinition parserDefinition) {
        addExplicitExtension(LanguageParserDefinitions.INSTANCE, language, parserDefinition);
    }

    public <T> void addExplicitExtension(@NotNull final FileTypeExtension<T> instance, @NotNull final FileType fileType, @NotNull final T object) {
        instance.addExplicitExtension(fileType, object, myParentDisposable);
    }

    public <T> void addExplicitExtension(@NotNull final ClassExtension<T> instance, @NotNull final Class aClass, @NotNull final T object) {
        instance.addExplicitExtension(aClass, object, myParentDisposable);
    }

    public <T> void addExtension(@NotNull ExtensionPointName<T> name, @NotNull final T extension) {
        final ExtensionPoint<T> extensionPoint = Extensions.getRootArea().getExtensionPoint(name);
        //noinspection TestOnlyProblems
        extensionPoint.registerExtension(extension, myParentDisposable);
    }

    @NotNull
    public CoreLocalFileSystem getLocalFileSystem() {
        return myLocalFileSystem;
    }

    @NotNull
    public VirtualFileSystem getJarFileSystem() {
        return myJarFileSystem;
    }

    @Nullable
    public VirtualFileSystem getJrtFileSystem() {
        return myJrtFileSystem;
    }
}