package com.raival.compose.file.explorer.screen.textEditor.filetype

/**
 * Interface representing a file type and its syntax highlighting metadata.
 */
interface FileType {
    val extensions: List<String>
    val names: List<String>?
        get() = null
    val textmateScope: String?
    val name: String
    val title: String
    val markdownNames: List<String>
        get() = emptyList()
}

/**
 * Manager responsible for identifying file type and textmate scope.
 */
object FileTypeManager {
    fun allTypes(): List<FileType> = BuiltinFileType.entries

    fun fromFileName(fileName: String): FileType {
        val normalized = fileName.lowercase()
        val ext = normalized.substringAfterLast('.', "")
        return allTypes().firstOrNull { it.names != null && normalized in it.names!! }
            ?: fromExtension(ext)
    }

    fun fromExtension(ext: String): FileType {
        val normalized = ext.lowercase().removePrefix(".")
        return allTypes().firstOrNull { normalized in it.extensions } ?: BuiltinFileType.UNKNOWN
    }

    fun fromScope(scope: String?): FileType {
        if (scope == null) return BuiltinFileType.UNKNOWN
        return allTypes().firstOrNull { it.textmateScope == scope } ?: BuiltinFileType.UNKNOWN
    }

    fun fromMarkdownName(name: String): FileType {
        val normalized = name.lowercase()
        return allTypes().firstOrNull { normalized in it.extensions || normalized in it.markdownNames }
            ?: BuiltinFileType.UNKNOWN
    }

    fun knowsExtension(ext: String): Boolean {
        val normalized = ext.lowercase().removePrefix(".")
        return allTypes().any { normalized in it.extensions }
    }
}

/**
 * Built-in file types covering 49+ languages supported by TextMate grammars.
 */
enum class BuiltinFileType(
    override val extensions: List<String>,
    override val names: List<String>? = null,
    override val textmateScope: String?,
    override val title: String,
    override val markdownNames: List<String> = emptyList(),
) : FileType {
    // Web
    JAVASCRIPT(
        extensions = listOf("js", "mjs", "cjs", "jscsrc", "jshintrc", "javascript"),
        textmateScope = "source.js",
        title = "JavaScript",
        markdownNames = listOf("javascript", "js"),
    ),
    TYPESCRIPT(
        extensions = listOf("ts", "mts", "cts", "typescript"),
        textmateScope = "source.ts",
        title = "TypeScript",
        markdownNames = listOf("typescript", "ts"),
    ),
    JSX(
        extensions = listOf("jsx"),
        textmateScope = "source.js.jsx",
        title = "JavaScript JSX",
        markdownNames = listOf("jsx"),
    ),
    TSX(
        extensions = listOf("tsx"),
        textmateScope = "source.tsx",
        title = "TypeScript JSX",
        markdownNames = listOf("tsx"),
    ),
    HTML(
        extensions = listOf("html", "htm", "xhtml", "xht"),
        textmateScope = "text.html.basic",
        title = "HTML",
        markdownNames = listOf("html"),
    ),
    HTMX(
        extensions = listOf("htmx"),
        textmateScope = "text.html.htmx",
        title = "HTMX",
    ),
    CSS(
        extensions = listOf("css"),
        textmateScope = "source.css",
        title = "CSS",
        markdownNames = listOf("css"),
    ),
    SCSS(
        extensions = listOf("scss", "sass"),
        textmateScope = "source.css.scss",
        title = "SCSS",
        markdownNames = listOf("scss", "sass"),
    ),
    LESS(
        extensions = listOf("less"),
        textmateScope = "source.css.less",
        title = "Less",
        markdownNames = listOf("less"),
    ),
    JSON(
        extensions = listOf("json", "jsonl", "jsonc"),
        textmateScope = "source.json",
        title = "JSON",
        markdownNames = listOf("json"),
    ),
    MARKDOWN(
        extensions = listOf("md", "markdown", "mdown", "mkd", "mkdn", "mdoc", "mdtext", "mdtxt", "mdwn"),
        textmateScope = "text.html.markdown",
        title = "Markdown",
        markdownNames = listOf("markdown", "md"),
    ),
    XML(
        extensions = listOf("xml", "xaml", "dtd", "plist", "ascx", "csproj", "wxi", "wxl", "wxs", "svg"),
        textmateScope = "text.xml",
        title = "XML",
        markdownNames = listOf("xml"),
    ),
    YAML(
        extensions = listOf("yaml", "yml", "eyaml", "eyml", "cff"),
        textmateScope = "source.yaml",
        title = "YAML",
        markdownNames = listOf("yaml", "yml"),
    ),

    // Programming Languages
    KOTLIN(
        extensions = listOf("kt", "kts"),
        textmateScope = "source.kotlin",
        title = "Kotlin",
        markdownNames = listOf("kotlin", "kt"),
    ),
    JAVA(
        extensions = listOf("java", "jav", "bsh"),
        textmateScope = "source.java",
        title = "Java",
        markdownNames = listOf("java"),
    ),
    PYTHON(
        extensions = listOf("py", "pyi", "pyw"),
        textmateScope = "source.python",
        title = "Python",
        markdownNames = listOf("python", "py"),
    ),
    C(
        extensions = listOf("c"),
        textmateScope = "source.c",
        title = "C",
        markdownNames = listOf("c"),
    ),
    CPP(
        extensions = listOf("cpp", "cxx", "cc", "c++", "h", "hpp", "hh", "hxx", "h++"),
        textmateScope = "source.cpp",
        title = "C++",
        markdownNames = listOf("cpp", "c++"),
    ),
    CSHARP(
        extensions = listOf("cs", "csx"),
        textmateScope = "source.cs",
        title = "C#",
        markdownNames = listOf("csharp", "cs"),
    ),
    RUST(
        extensions = listOf("rs"),
        textmateScope = "source.rust",
        title = "Rust",
        markdownNames = listOf("rust", "rs"),
    ),
    GO(
        extensions = listOf("go"),
        textmateScope = "source.go",
        title = "Go",
        markdownNames = listOf("go", "golang"),
    ),
    PHP(
        extensions = listOf("php", "phtml", "php3", "php4", "php5", "php7", "phps"),
        textmateScope = "source.php",
        title = "PHP",
        markdownNames = listOf("php"),
    ),
    RUBY(
        extensions = listOf("rb", "erb", "gemspec", "rake"),
        textmateScope = "source.ruby",
        title = "Ruby",
        markdownNames = listOf("ruby", "rb"),
    ),
    SWIFT(
        extensions = listOf("swift"),
        textmateScope = "source.swift",
        title = "Swift",
        markdownNames = listOf("swift"),
    ),
    DART(
        extensions = listOf("dart"),
        textmateScope = "source.dart",
        title = "Dart",
        markdownNames = listOf("dart"),
    ),
    LUA(
        extensions = listOf("lua", "luau"),
        textmateScope = "source.lua",
        title = "Lua",
        markdownNames = listOf("lua"),
    ),
    SHELL(
        extensions = listOf(
            "sh", "bash", "bash_login", "bash_logout", "bash_profile", "bashrc",
            "profile", "rhistory", "rprofile", "zsh", "zlogin", "zlogout",
            "zprofile", "zshenv", "zshrc", "fish", "ksh"
        ),
        textmateScope = "source.shell",
        title = "Shell script",
        markdownNames = listOf("shell", "bash", "sh", "zsh"),
    ),
    WINDOWS_SHELL(
        extensions = listOf("cmd", "bat"),
        textmateScope = "source.batchfile",
        title = "Batch",
        markdownNames = listOf("batch", "bat", "cmd"),
    ),
    POWERSHELL(
        extensions = listOf("ps1", "psm1", "psd1"),
        textmateScope = "source.powershell",
        title = "PowerShell",
        markdownNames = listOf("powershell", "ps"),
    ),
    SQL(
        extensions = listOf("sql", "dsql", "sqlite", "sqlite3"),
        textmateScope = "source.sql",
        title = "SQL",
        markdownNames = listOf("sql"),
    ),
    GROOVY(
        extensions = listOf("gsh", "groovy", "gradle", "gvy", "gy"),
        textmateScope = "source.groovy",
        title = "Groovy",
        markdownNames = listOf("groovy", "gradle"),
    ),
    ZIG(
        extensions = listOf("zig", "zon"),
        textmateScope = "source.zig",
        title = "Zig",
        markdownNames = listOf("zig"),
    ),
    NIM(
        extensions = listOf("nim", "nims", "nimble"),
        textmateScope = "source.nim",
        title = "Nim",
        markdownNames = listOf("nim"),
    ),
    PASCAL(
        extensions = listOf("p", "pas"),
        textmateScope = "source.pascal",
        title = "Pascal",
        markdownNames = listOf("pascal"),
    ),
    LISP(
        extensions = listOf("lisp", "clisp", "el", "scm"),
        textmateScope = "source.lisp",
        title = "Lisp",
        markdownNames = listOf("lisp"),
    ),
    ASSEMBLY(
        extensions = listOf("asm", "s", "S"),
        textmateScope = "source.asm",
        title = "Assembly",
        markdownNames = listOf("assembly", "asm"),
    ),
    CMAKE(
        extensions = listOf("cmake"),
        names = listOf("cmakelists.txt"),
        textmateScope = "source.cmake",
        title = "CMake",
        markdownNames = listOf("cmake"),
    ),
    R(
        extensions = listOf("r", "rmd"),
        textmateScope = "source.r",
        title = "R",
        markdownNames = listOf("r"),
    ),
    NIX(
        extensions = listOf("nix"),
        textmateScope = "source.nix",
        title = "Nix",
        markdownNames = listOf("nix"),
    ),
    SMALI(
        extensions = listOf("smali"),
        textmateScope = "source.smali",
        title = "Smali",
        markdownNames = listOf("smali"),
    ),
    ROCQ(
        extensions = listOf("v", "coq"),
        textmateScope = "source.coq",
        title = "Rocq (Coq)",
        markdownNames = listOf("coq"),
    ),
    LATEX(
        extensions = listOf("latex", "tex", "ltx", "bib"),
        textmateScope = "text.tex.latex",
        title = "LaTeX",
        markdownNames = listOf("latex", "tex"),
    ),

    // Config & Data
    TOML(
        extensions = listOf("toml"),
        textmateScope = "source.toml",
        title = "TOML",
        markdownNames = listOf("toml"),
    ),
    INI(
        extensions = listOf("ini"),
        textmateScope = "source.ini",
        title = "INI",
        markdownNames = listOf("ini"),
    ),
    PROPERTIES(
        extensions = listOf(
            "properties", "cfg", "conf", "config", "editorconfig",
            "gitconfig", "gitmodules", "gitattributes"
        ),
        textmateScope = "source.properties",
        title = "Properties",
    ),
    IGNORE(
        extensions = listOf("gitignore", "gitignore_global", "gitkeep", "git-blame-ignore-revs"),
        textmateScope = "source.ignore",
        title = "Ignore",
    ),
    DIFF(
        extensions = listOf("diff", "patch", "rej"),
        textmateScope = "source.diff",
        title = "Diff",
        markdownNames = listOf("diff", "patch"),
    ),
    LOG(
        extensions = listOf("log"),
        textmateScope = "text.log",
        title = "Log",
        markdownNames = listOf("log"),
    ),
    TEXT(
        extensions = listOf("txt"),
        textmateScope = "text.plain",
        title = "Plain text",
        markdownNames = listOf("plaintext", "text", "txt"),
    ),
    UNKNOWN(
        extensions = emptyList(),
        textmateScope = null,
        title = "Plain text",
    );
}
