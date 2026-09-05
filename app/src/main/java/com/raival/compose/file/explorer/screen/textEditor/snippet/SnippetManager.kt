package com.raival.compose.file.explorer.screen.textEditor.snippet

import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.SimpleSnippetCompletionItem
import io.github.rosemoe.sora.lang.completion.SnippetDescription
import io.github.rosemoe.sora.lang.completion.snippet.CodeSnippet
import io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser
import java.util.concurrent.ConcurrentHashMap

data class SnippetTemplate(
    val label: String,
    val description: String,
    val template: String,
    val scopes: List<String>,
)

object SnippetManager {
    private val parsedSnippetCache = ConcurrentHashMap<String, CodeSnippet>()
    private val snippetRegistry = mutableListOf<SnippetTemplate>()

    init {
        registerBuiltinSnippets()
    }

    private fun parseSnippet(template: String): CodeSnippet {
        return parsedSnippetCache.computeIfAbsent(template) {
            try {
                CodeSnippetParser.parse(it)
            } catch (e: Exception) {
                CodeSnippetParser.parse(template.replace(Regex("\\$\\{?\\d+(:[^}]*)?\\}?"), ""))
            }
        }
    }

    fun getSnippetsForScope(scopeName: String, prefix: String): List<CompletionItem> {
        if (prefix.isEmpty()) return emptyList()

        val matchingSnippets = snippetRegistry.filter { snippet ->
            (snippet.scopes.isEmpty() || snippet.scopes.any { scopeName.startsWith(it) || it == "*" }) &&
                    snippet.label.startsWith(prefix, ignoreCase = true)
        }

        return matchingSnippets.map { snippet ->
            val codeSnippet = parseSnippet(snippet.template)
            SimpleSnippetCompletionItem(
                snippet.label,
                snippet.description,
                SnippetDescription(prefix.length, codeSnippet, true)
            )
        }
    }

    private fun register(
        label: String,
        description: String,
        template: String,
        vararg scopes: String
    ) {
        snippetRegistry.add(
            SnippetTemplate(
                label = label,
                description = description,
                template = template,
                scopes = scopes.toList()
            )
        )
    }

    private fun registerBuiltinSnippets() {
        // ==================== KOTLIN ====================
        register("fun", "Function definition", "fun \${1:name}(\${2:params}): \${3:Unit} {\n    \$0\n}", "source.kotlin")
        register("funmain", "Main function", "fun main(args: Array<String>) {\n    \$0\n}", "source.kotlin")
        register("class", "Class definition", "class \${1:ClassName}\${2:(\${3:params})} {\n    \$0\n}", "source.kotlin")
        register("dataclass", "Data class", "data class \${1:ClassName}(\n    val \${2:property}: \${3:String}\n)", "source.kotlin")
        register("interface", "Interface definition", "interface \${1:InterfaceName} {\n    \$0\n}", "source.kotlin")
        register("for", "For-in loop", "for (\${1:item} in \${2:collection}) {\n    \$0\n}", "source.kotlin")
        register("fori", "Indexed loop", "for (\${1:i} in \${2:0} until \${3:count}) {\n    \$0\n}", "source.kotlin")
        register("if", "If statement", "if (\${1:condition}) {\n    \$0\n}", "source.kotlin")
        register("ife", "If-Else statement", "if (\${1:condition}) {\n    \$2\n} else {\n    \$0\n}", "source.kotlin")
        register("when", "When expression", "when (\${1:expression}) {\n    \${2:value} -> \$3\n    else -> \$0\n}", "source.kotlin")
        register("try", "Try-Catch block", "try {\n    \$1\n} catch (e: \${2:Exception}) {\n    \$0\n}", "source.kotlin")
        register("sout", "Print line", "println(\${1:\"message\"})", "source.kotlin")
        register("val", "Read-only property", "val \${1:name}: \${2:String} = \${3:value}", "source.kotlin")
        register("var", "Mutable property", "var \${1:name}: \${2:String} = \${3:value}", "source.kotlin")

        // ==================== JAVA ====================
        register("psvm", "Main method", "public static void main(String[] args) {\n    \$0\n}", "source.java")
        register("sout", "Print line", "System.out.println(\${1:\"message\"});", "source.java")
        register("fori", "For loop with index", "for (int \${1:i} = 0; \$1 < \${2:count}; \$1++) {\n    \$0\n}", "source.java")
        register("foreach", "For-each loop", "for (\${1:Type} \${2:item} : \${3:collection}) {\n    \$0\n}", "source.java")
        register("if", "If statement", "if (\${1:condition}) {\n    \$0\n}", "source.java")
        register("ife", "If-Else statement", "if (\${1:condition}) {\n    \$2\n} else {\n    \$0\n}", "source.java")
        register("try", "Try-Catch block", "try {\n    \$1\n} catch (\${2:Exception} e) {\n    \$0\n}", "source.java")
        register("class", "Class definition", "public class \${1:ClassName} {\n    \$0\n}", "source.java")
        register("interface", "Interface definition", "public interface \${1:InterfaceName} {\n    \$0\n}", "source.java")

        // ==================== PYTHON ====================
        register("def", "Function definition", "def \${1:func_name}(\${2:params}):\n    \${0:pass}", "source.python")
        register("class", "Class definition", "class \${1:ClassName}:\n    def __init__(self\${2:, params}):\n        \${0:pass}", "source.python")
        register("for", "For in loop", "for \${1:item} in \${2:iterable}:\n    \$0", "source.python")
        register("fori", "Range loop", "for \${1:i} in range(\${2:count}):\n    \$0", "source.python")
        register("if", "If statement", "if \${1:condition}:\n    \$0", "source.python")
        register("ife", "If-Else statement", "if \${1:condition}:\n    \$2\nelse:\n    \$0", "source.python")
        register("while", "While loop", "while \${1:condition}:\n    \$0", "source.python")
        register("try", "Try-Except block", "try:\n    \$1\nexcept \${2:Exception} as e:\n    \$0", "source.python")
        register("with", "With open statement", "with open(\${1:\"file.txt\"}, \"\${2:r}\") as \${3:f}:\n    \$0", "source.python")
        register("main", "Name == main block", "if __name__ == \"__main__\":\n    \${0:main()}", "source.python")
        register("print", "Print function", "print(\${1:\"message\"})", "source.python")

        // ==================== JAVASCRIPT / TYPESCRIPT ====================
        register("clg", "Console log", "console.log(\${1:\"message\"});", "source.js", "source.ts", "source.tsx")
        register("fun", "Function statement", "function \${1:name}(\${2:params}) {\n    \$0\n}", "source.js", "source.ts", "source.tsx")
        register("arrow", "Arrow function", "const \${1:name} = (\${2:params}) => {\n    \$0\n};", "source.js", "source.ts", "source.tsx")
        register("async", "Async function", "async function \${1:name}(\${2:params}) {\n    \$0\n}", "source.js", "source.ts", "source.tsx")
        register("for", "For loop", "for (let \${1:i} = 0; \$1 < \${2:count}; \$1++) {\n    \$0\n}", "source.js", "source.ts", "source.tsx")
        register("forof", "For-of loop", "for (const \${1:item} of \${2:iterable}) {\n    \$0\n}", "source.js", "source.ts", "source.tsx")
        register("forin", "For-in loop", "for (const \${1:key} in \${2:object}) {\n    \$0\n}", "source.js", "source.ts", "source.tsx")
        register("if", "If statement", "if (\${1:condition}) {\n    \$0\n}", "source.js", "source.ts", "source.tsx")
        register("ife", "If-Else statement", "if (\${1:condition}) {\n    \$2\n} else {\n    \$0\n}", "source.js", "source.ts", "source.tsx")
        register("try", "Try-Catch block", "try {\n    \$1\n} catch (\${2:error}) {\n    \$0\n}", "source.js", "source.ts", "source.tsx")
        register("import", "Import module", "import { \${1:item} } from \"\${2:module}\";", "source.js", "source.ts", "source.tsx")
        register("export", "Export const", "export const \${1:name} = \$0;", "source.js", "source.ts", "source.tsx")

        // ==================== HTML / HTMX ====================
        register(
            "html5",
            "HTML5 template",
            "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>\${1:Document}</title>\n</head>\n<body>\n    \$0\n</body>\n</html>",
            "text.html.basic", "text.html.htmx"
        )
        register("div", "Div container", "<div class=\"\${1:container}\">\n    \$0\n</div>", "text.html.basic", "text.html.htmx")
        register("a", "Hyperlink", "<a href=\"\${1:#}\">\${2:Link}</a>", "text.html.basic", "text.html.htmx")
        register("button", "Button element", "<button type=\"\${1:button}\">\${2:Button}</button>", "text.html.basic", "text.html.htmx")
        register("form", "Form element", "<form action=\"\${1:#}\" method=\"\${2:POST}\">\n    \$0\n</form>", "text.html.basic", "text.html.htmx")
        register("input", "Input element", "<input type=\"\${1:text}\" name=\"\${2:name}\" placeholder=\"\${3:placeholder}\" />", "text.html.basic", "text.html.htmx")
        register("link", "Stylesheet link", "<link rel=\"stylesheet\" href=\"\${1:style.css}\" />", "text.html.basic", "text.html.htmx")
        register("script", "Script tag", "<script src=\"\${1:main.js}\"></script>", "text.html.basic", "text.html.htmx")
        register("hxget", "HTMX GET request", "hx-get=\"\${1:/api/path}\" hx-target=\"\${2:#target}\" hx-swap=\"\${3:innerHTML}\"", "text.html.htmx", "text.html.basic")
        register("hxpost", "HTMX POST request", "hx-post=\"\${1:/api/path}\" hx-target=\"\${2:#target}\" hx-swap=\"\${3:innerHTML}\"", "text.html.htmx", "text.html.basic")

        // ==================== C / C++ ====================
        register("main", "Main function", "int main(int argc, char* argv[]) {\n    \$0\n    return 0;\n}", "source.c", "source.cpp")
        register("include", "Include header", "#include <\${1:stdio.h}>", "source.c", "source.cpp")
        register("includecpp", "Include C++ iostream", "#include <\${1:iostream}>\nusing namespace std;\n", "source.cpp")
        register("cout", "Print to stdout", "std::cout << \${1:\"message\"} << std::endl;", "source.cpp")
        register("printf", "Print formatted", "printf(\"\${1:%s\\n}\", \${2:message});", "source.c", "source.cpp")
        register("for", "For loop", "for (int \${1:i} = 0; \$1 < \${2:n}; \$1++) {\n    \$0\n}", "source.c", "source.cpp")
        register("struct", "Struct definition", "struct \${1:Name} {\n    \$0\n};", "source.c", "source.cpp")
        register("class", "Class definition", "class \${1:ClassName} {\npublic:\n    \${1:ClassName}();\n    ~\$1();\nprivate:\n    \$0\n};", "source.cpp")

        // ==================== RUST ====================
        register("fn", "Function definition", "fn \${1:name}(\${2:params}) -> \${3:()} {\n    \$0\n}", "source.rust")
        register("main", "Main function", "fn main() {\n    \$0\n}", "source.rust")
        register("println", "Print line macro", "println!(\"\${1:{}}\", \${2:value});", "source.rust")
        register("struct", "Struct definition", "struct \${1:Name} {\n    \${2:field}: \${3:Type},\n}", "source.rust")
        register("impl", "Impl block", "impl \${1:Name} {\n    \$0\n}", "source.rust")
        register("match", "Match pattern", "match \${1:expr} {\n    \${2:pattern} => \$3,\n    _ => \$0,\n}", "source.rust")
        register("for", "For loop", "for \${1:item} in \${2:iter} {\n    \$0\n}", "source.rust")

        // ==================== GO ====================
        register("main", "Package main with func main", "package main\n\nimport \"fmt\"\n\nfunc main() {\n    \$0\n}", "source.go")
        register("func", "Function definition", "func \${1:name}(\${2:params}) \${3:error} {\n    \$0\n}", "source.go")
        register("struct", "Type struct definition", "type \${1:Name} struct {\n    \$0\n}", "source.go")
        register("interface", "Type interface definition", "type \${1:Name} interface {\n    \$0\n}", "source.go")
        register("iferr", "Error check", "if err != nil {\n    return \${1:err}\n}", "source.go")
        register("for", "For loop", "for \${1:i} := 0; \$1 < \${2:n}; \$1++ {\n    \$0\n}", "source.go")
        register("forr", "For range loop", "for \${1:k}, \${2:v} := range \${3:collection} {\n    \$0\n}", "source.go")
        register("println", "Fmt Println", "fmt.Println(\${1:\"message\"})", "source.go")

        // ==================== SHELL ====================
        register("shebang", "Bash shebang", "#!/usr/bin/env bash\n\n\$0", "source.shell")
        register("if", "If condition", "if [ \${1:condition} ]; then\n    \$0\nfi", "source.shell")
        register("ife", "If-Else condition", "if [ \${1:condition} ]; then\n    \$2\nelse\n    \$0\nfi", "source.shell")
        register("for", "For loop", "for \${1:item} in \${2:list}; do\n    \$0\ndone", "source.shell")
        register("case", "Case switch", "case \"\${1:var}\" in\n    \${2:pattern})\n        \$3\n        ;;\n    *)\n        \$0\n        ;;\nesac", "source.shell")

        // ==================== MARKDOWN ====================
        register("table", "Markdown table", "| \${1:Header 1} | \${2:Header 2} |\n| --- | --- |\n| \${3:Data 1} | \${4:Data 2} |\n\$0", "text.html.markdown")
        register("code", "Fenced codeblock", "```\${1:language}\n\$0\n```", "text.html.markdown")
        register("link", "Link", "[\${1:text}](\${2:url})", "text.html.markdown")
        register("image", "Image", "![\${1:alt}](\${2:url})", "text.html.markdown")
        register("task", "Task item", "- [ ] \${1:Task}", "text.html.markdown")
    }
}
