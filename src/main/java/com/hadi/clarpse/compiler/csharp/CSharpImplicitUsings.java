package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.ProjectFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The namespaces the .NET SDK imports into every file of a project that enables
 * {@code <ImplicitUsings>}.
 *
 * <p>A project file enables them with {@code enable} or {@code true}. A project file that does not
 * say inherits the setting of the nearest {@code Directory.Build.props} above it. The namespaces
 * are the SDK's documented default lists, by project SDK: the base list of {@code Microsoft.NET.Sdk}
 * plus, for {@code Microsoft.NET.Sdk.Web} and {@code Microsoft.NET.Sdk.Worker}, the additions
 * (https://learn.microsoft.com/dotnet/core/project-sdk/overview#implicit-using-directives). Every
 * C# file below a project file's directory, and not below the directory of a nearer project file,
 * belongs to that project.
 */
final class CSharpImplicitUsings {

    private static final List<String> BASE = List.of(
            "System", "System.Collections.Generic", "System.IO", "System.Linq", "System.Net.Http",
            "System.Threading", "System.Threading.Tasks");

    private static final List<String> WEB = List.of(
            "System.Net.Http.Json", "Microsoft.AspNetCore.Builder", "Microsoft.AspNetCore.Hosting",
            "Microsoft.AspNetCore.Http", "Microsoft.AspNetCore.Routing",
            "Microsoft.Extensions.Configuration", "Microsoft.Extensions.DependencyInjection",
            "Microsoft.Extensions.Hosting", "Microsoft.Extensions.Logging");

    private static final List<String> WORKER = List.of(
            "Microsoft.Extensions.Configuration", "Microsoft.Extensions.DependencyInjection",
            "Microsoft.Extensions.Hosting", "Microsoft.Extensions.Logging");

    private static final Pattern ENABLED = Pattern.compile(
            "<ImplicitUsings>\\s*(enable|true)\\s*</ImplicitUsings>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SET = Pattern.compile("<ImplicitUsings>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SDK = Pattern.compile("<Project\\b[^>]*\\bSdk\\s*=\\s*\"([^\"]+)\"");

    private CSharpImplicitUsings() {
    }

    /**
     * The implicit namespaces of each source file that has any.
     *
     * @param configFiles Every project file in the repository, of any kind; the project files and
     *                    {@code Directory.Build.props} are picked out here.
     * @param sources     The C# files.
     * @return The namespaces by source path, for the files of projects that enable implicit usings.
     */
    static Map<String, List<String>> forFiles(final Collection<ProjectFile> configFiles,
                                              final Collection<ProjectFile> sources) {
        final Map<String, ProjectFile> projects = new LinkedHashMap<>();
        final Map<String, ProjectFile> props = new LinkedHashMap<>();
        for (final ProjectFile file : configFiles) {
            final String path = normalize(file.path());
            if (path.endsWith(".csproj")) {
                projects.put(path, file);
            } else if (path.equals("directory.build.props") || path.endsWith("/directory.build.props")) {
                props.put(path, file);
            }
        }
        final Map<String, List<String>> result = new LinkedHashMap<>();
        if (projects.isEmpty()) {
            return result;
        }
        for (final ProjectFile source : sources) {
            final String project = owningProject(normalize(source.path()), projects.keySet());
            if (project == null) {
                continue;
            }
            final ProjectFile projectFile = projects.get(project);
            Boolean enabled = settingOf(projectFile.content());
            if (enabled == null) {
                enabled = inheritedSetting(project, props);
            }
            if (Boolean.TRUE.equals(enabled)) {
                result.put(source.path(), namespacesFor(projectFile.content()));
            }
        }
        return result;
    }

    private static String normalize(final String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }

    /** The project file in the nearest enclosing directory of the source, or null. */
    private static String owningProject(final String sourcePath, final Set<String> projectPaths) {
        String directory = parentOf(sourcePath);
        while (true) {
            for (final String project : projectPaths) {
                if (parentOf(project).equals(directory)) {
                    return project;
                }
            }
            if (directory.isEmpty()) {
                return null;
            }
            directory = parentOf(directory);
        }
    }

    private static String parentOf(final String path) {
        final int slash = path.lastIndexOf('/');
        if (slash < 0) {
            return "";
        }
        return path.substring(0, slash);
    }

    private static Boolean settingOf(final String content) {
        if (content == null || !SET.matcher(content).find()) {
            return null;
        }
        return ENABLED.matcher(content).find();
    }

    private static Boolean inheritedSetting(final String projectPath, final Map<String, ProjectFile> props) {
        String directory = parentOf(projectPath);
        while (true) {
            final String candidate;
            if (directory.isEmpty()) {
                candidate = "directory.build.props";
            } else {
                candidate = directory + "/directory.build.props";
            }
            final ProjectFile file = props.get(candidate);
            if (file != null) {
                final Boolean setting = settingOf(file.content());
                if (setting != null) {
                    return setting;
                }
            }
            if (directory.isEmpty()) {
                return null;
            }
            directory = parentOf(directory);
        }
    }

    private static List<String> namespacesFor(final String projectContent) {
        final Set<String> namespaces = new LinkedHashSet<>(BASE);
        final Matcher sdk = SDK.matcher(projectContent == null ? "" : projectContent);
        if (sdk.find()) {
            final String name = sdk.group(1);
            if ("Microsoft.NET.Sdk.Web".equalsIgnoreCase(name)) {
                namespaces.addAll(WEB);
            } else if ("Microsoft.NET.Sdk.Worker".equalsIgnoreCase(name)) {
                namespaces.addAll(WORKER);
            }
        }
        return new ArrayList<>(namespaces);
    }
}
