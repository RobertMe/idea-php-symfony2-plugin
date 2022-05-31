package fr.adrienbrault.idea.symfony2plugin.util.resource;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.PlatformIcons;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.PhpIcons;
import fr.adrienbrault.idea.symfony2plugin.stubs.cache.FileIndexCaches;
import fr.adrienbrault.idea.symfony2plugin.stubs.indexes.FileResourcesIndex;
import fr.adrienbrault.idea.symfony2plugin.util.*;
import fr.adrienbrault.idea.symfony2plugin.util.dict.SymfonyBundle;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class FileResourceUtil {

    /**
     * Search for files refers to given file
     */
    @NotNull
    public static Collection<VirtualFile> getFileResourceRefers(@NotNull Project project, @NotNull VirtualFile virtualFile) {
        String bundleLocateName = getBundleLocateName(project, virtualFile);
        if(bundleLocateName == null) {
            return Collections.emptyList();
        }

        return getFileResourceRefers(project, bundleLocateName);
    }

    /**
     * Search for files refers to given file
     */
    @NotNull
    private static Collection<VirtualFile> getFileResourceRefers(@NotNull Project project, @NotNull String bundleLocateName) {
        return FileBasedIndex.getInstance().getContainingFiles(
            FileResourcesIndex.KEY,
            bundleLocateName,
            GlobalSearchScope.allScope(project)
        );
    }

    /**
     * Search for files refers to given file
     */
    public static boolean hasFileResources(@NotNull Project project, @NotNull PsiFile psiFile) {
        return CachedValuesManager.getCachedValue(
            psiFile,
            () -> {
                VirtualFile virtualFile = psiFile.getVirtualFile();
                if (virtualFile == null) {
                    return CachedValueProvider.Result.create(Boolean.FALSE, FileIndexCaches.getModificationTrackerForIndexId(project, FileResourcesIndex.KEY));
                }

                Set<String> collect = FileBasedIndex.getInstance().getAllKeys(FileResourcesIndex.KEY, project)
                    .stream()
                    .filter(s -> !s.startsWith("@"))
                    .collect(Collectors.toSet());

                for (String s : collect) {
                    for (VirtualFile containingFile : FileBasedIndex.getInstance().getContainingFiles(FileResourcesIndex.KEY, s, GlobalSearchScope.allScope(project))) {
                        VirtualFile directory = containingFile.getParent();
                        if (directory == null) {
                            continue;
                        }

                        VirtualFile relativeFile = VfsUtil.findRelativeFile(directory, s.replace("\\", "/").split("/"));
                        if (relativeFile != null) {
                            String relativePath = VfsUtil.getRelativePath(virtualFile, relativeFile);
                            if (relativePath != null) {
                                return CachedValueProvider.Result.create(Boolean.TRUE, FileIndexCaches.getModificationTrackerForIndexId(project, FileResourcesIndex.KEY));
                            }
                        }
                    }
                }

                return CachedValueProvider.Result.create(Boolean.FALSE, FileIndexCaches.getModificationTrackerForIndexId(project, FileResourcesIndex.KEY));
            }
        );
    }

    /**
     * Search for files refers to given file
     */
    @NotNull
    public static Collection<Pair<VirtualFile, String>> getFileResources(@NotNull Project project, @NotNull VirtualFile virtualFile) {
        Set<String> collect = FileBasedIndex.getInstance().getAllKeys(FileResourcesIndex.KEY, project)
            .stream()
            .filter(s -> !s.startsWith("@"))
            .collect(Collectors.toSet());

        Collection<Pair<VirtualFile, String>> files = new ArrayList<>();
        for (String s : collect) {
            for (VirtualFile containingFile : FileBasedIndex.getInstance().getContainingFiles(FileResourcesIndex.KEY, s, GlobalSearchScope.allScope(project))) {
                VirtualFile directory = containingFile.getParent();
                if (directory == null) {
                    continue;
                }

                VirtualFile relativeFile = VfsUtil.findRelativeFile(directory, s.replace("\\", "/").split("/"));
                if (relativeFile != null) {
                    String relativePath = VfsUtil.getRelativePath(virtualFile, relativeFile);
                    if (relativePath != null) {
                        files.add(Pair.create(containingFile, s));
                    }
                }
            }
        }

        return files;
    }

    @Nullable
    public static String getBundleLocateName(@NotNull Project project, @NotNull VirtualFile virtualFile) {
        SymfonyBundle containingBundle = new SymfonyBundleUtil(project).getContainingBundle(virtualFile);
        if(containingBundle == null) {
            return null;
        }

        String relativePath = containingBundle.getRelativePath(virtualFile);
        if(relativePath == null) {
            return null;
        }

        return "@" + containingBundle.getName() + "/" + relativePath;
    }

    /**
     * Search for line definition of "@FooBundle/foo.xml"
     */
    @NotNull
    private static Collection<PsiElement> getBundleLocateStringDefinitions(@NotNull Project project, final @NotNull String bundleFileName) {
        final Collection<PsiElement> psiElements = new HashSet<>();
        for (VirtualFile refVirtualFile : getFileResourceRefers(project, bundleFileName)) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(refVirtualFile);
            if(psiFile == null) {
                continue;
            }

            FileResourceVisitorUtil.visitFile(psiFile, consumer -> {
                if (bundleFileName.equals(consumer.getResource())) {
                    psiElements.add(consumer.getPsiElement());
                }
            });
        }

        return psiElements;
    }

    @Nullable
    public static RelatedItemLineMarkerInfo<PsiElement> getFileImplementsLineMarker(@NotNull PsiFile psiFile) {
        final Project project = psiFile.getProject();

        VirtualFile virtualFile = psiFile.getVirtualFile();
        if(virtualFile == null) {
            return null;
        }

        String bundleLocateName = FileResourceUtil.getBundleLocateName(project, virtualFile);
        if(bundleLocateName != null) {
            if(FileResourceUtil.getFileResourceRefers(project, bundleLocateName).size() == 0) {
                return null;
            }

            NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder.create(PhpIcons.IMPLEMENTS)
                .setTargets(NotNullLazyValue.lazy(new FileResourceBundleNotNullLazyValue(project, bundleLocateName)))
                .setTooltipText("Navigate to resource");

            return builder.createLineMarkerInfo(psiFile);
        } else {
            if (hasFileResources(project, psiFile)) {
                NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder.create(PhpIcons.IMPLEMENTS);
                builder.setTargets(NotNullLazyValue.lazy(new FileResourceNotNullLazyValue(project, virtualFile)));

                builder.setTooltipText("Navigate to resource");

                return builder.createLineMarkerInfo(psiFile);
            }
        }

        return null;
    }

    /**
     * On route annotations we can have folder scope so: "@FooBundle/Controller/foo.php" can be equal "@FooBundle/Controller/"
     */
    @Nullable
    public static RelatedItemLineMarkerInfo<PsiElement> getFileImplementsLineMarkerInFolderScope(@NotNull PsiFile psiFile) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if(virtualFile == null) {
            return null;
        }

        final Project project = psiFile.getProject();
        String bundleLocateName = FileResourceUtil.getBundleLocateName(project, virtualFile);
        if(bundleLocateName == null) {
            return null;
        }

        Set<String> names = new HashSet<>();
        names.add(bundleLocateName);

        // strip filename
        int i = bundleLocateName.lastIndexOf("/");
        if(i > 0) {
            names.add(bundleLocateName.substring(0, i));
        }

        int targets = 0;
        for (String name : names) {
            targets += FileResourceUtil.getFileResourceRefers(project, name).size();
        }

        if(targets == 0) {
            return null;
        }

        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder.create(PlatformIcons.ANNOTATION_TYPE_ICON)
            .setTargets(NotNullLazyValue.lazy(new FileResourceBundleNotNullLazyValue(project, names)))
            .setTooltipText("Symfony: <a href=\"https://symfony.com/doc/current/routing.html#creating-routes-as-annotations\">Annotation Routing</a>");

        return builder.createLineMarkerInfo(psiFile);
    }

    private static class FileResourceBundleNotNullLazyValue implements Supplier<Collection<? extends PsiElement>> {

        private final Collection<String> resources;
        private final Project project;

        public FileResourceBundleNotNullLazyValue(@NotNull Project project, @NotNull Collection<String> resource) {
            this.resources = resource;
            this.project = project;
        }

        public FileResourceBundleNotNullLazyValue(@NotNull Project project, @NotNull String resource) {
            this.resources = Collections.singleton(resource);
            this.project = project;
        }

        @Override
        public Collection<? extends PsiElement> get() {
            Collection<PsiElement> psiElements = new HashSet<>();

            for (String resource : this.resources) {
                psiElements.addAll(getBundleLocateStringDefinitions(project, resource));
            }

            return psiElements;
        }
    }

    /**
     * Gives targets to files on Bundle locate syntax. "@FooBundle/.../foo.yml"
     */
    @NotNull
    public static Collection<PsiFile> getFileResourceTargetsInBundleScope(@NotNull Project project, @NotNull String content) {

        // min validation "@FooBundle/foo.yml"
        if(!content.startsWith("@") || !content.contains("/")) {
            return Collections.emptyList();
        }

        String bundleName = content.substring(1, content.indexOf("/"));

        Collection<PsiFile> targets = new HashSet<>();

        for (SymfonyBundle bundle : new SymfonyBundleUtil(project).getBundle(bundleName)) {
            String path = content.substring(content.indexOf("/") + 1);
            PsiFile psiFile = PsiElementUtils.virtualFileToPsiFile(project, bundle.getRelative(path));

            if(psiFile != null) {
                targets.add(psiFile);
            }
        }

        return targets;
    }

    /**
     * resource: "@AppBundle/Controller/"
     */
    @NotNull
    public static Collection<PsiElement> getFileResourceTargetsInBundleDirectory(@NotNull Project project, @NotNull String content) {
        // min validation "@FooBundle/foo.yml"
        if(!content.startsWith("@")) {
            return Collections.emptyList();
        }

        content = content.replace("/", "\\");
        if(!content.contains("\\")) {
            return Collections.emptyList();
        }

        String bundleName = content.substring(1, content.indexOf("\\"));

        if(new SymfonyBundleUtil(project).getBundle(bundleName).size() == 0) {
            return Collections.emptyList();
        }

        // support double backslashes
        content = content.replaceAll("\\\\+", "\\\\");
        String namespaceName = "\\" + StringUtils.strip(content.substring(1), "\\");
        return new ArrayList<>(PhpIndexUtil.getPhpClassInsideNamespace(
            project, namespaceName
        ));
    }

    /**
     * Gives targets to files which relative to current file directory
     */
    @NotNull
    public static Collection<PsiFile> getFileResourceTargetsInDirectoryScope(@NotNull PsiFile psiFile, @NotNull String content) {

        // bundle scope
        if(content.startsWith("@")) {
            return Collections.emptyList();
        }

        PsiDirectory containingDirectory = psiFile.getContainingDirectory();
        if(containingDirectory == null) {
            return Collections.emptyList();
        }

        VirtualFile relativeFile = VfsUtil.findRelativeFile(content, containingDirectory.getVirtualFile());
        if(relativeFile == null) {
            return Collections.emptyList();
        }

        Set<PsiFile> psiFiles = new HashSet<>();
        if (relativeFile.isDirectory()) {
            String path = relativeFile.getPath();
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:/**/*.php");

            Set<String> files = new HashSet<>();
            try {
                Files.walkFileTree(Paths.get(path), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                        if (pathMatcher.matches(path)) {
                            files.add(path.toString());
                        }
                        return files.size() < 200 ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) {
            }

            Set<PsiFile> collect = files.stream()
                .map(s -> VfsUtil.findFileByIoFile(new File(s), false))
                .filter(Objects::nonNull)
                .map(virtualFile -> PsiElementUtils.virtualFileToPsiFile(psiFile.getProject(), virtualFile))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            psiFiles.addAll(collect);
        } else {
            PsiFile targetFile = PsiElementUtils.virtualFileToPsiFile(psiFile.getProject(), relativeFile);
            if(targetFile != null) {
                psiFiles.add(targetFile);
            }
        }

        return psiFiles;
    }

    private static class FileResourceNotNullLazyValue implements Supplier<Collection<? extends PsiElement>> {
        private final Project project;
        private final VirtualFile virtualFile;

        public FileResourceNotNullLazyValue(Project project, VirtualFile virtualFile) {
            this.project = project;
            this.virtualFile = virtualFile;
        }

        @Override
        public Collection<? extends PsiElement> get() {
            Collection<PsiElement> psiElements = new HashSet<>();

            for (Pair<VirtualFile, String> pair : getFileResources(project, virtualFile)) {
                PsiFile psiFile1 = PsiElementUtils.virtualFileToPsiFile(project, pair.getFirst());
                if (psiFile1 == null) {
                    continue;
                }

                FileResourceVisitorUtil.visitFile(psiFile1, fileResourceConsumer -> {
                    if (fileResourceConsumer.getResource().equalsIgnoreCase(pair.getSecond())) {
                        psiElements.add(fileResourceConsumer.getPsiElement());
                    }
                });
            }

            return psiElements;
        }
    }
}
