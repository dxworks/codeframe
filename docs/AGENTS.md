# Project Scope
See the [README](README.md) for overall project scope

# Design guidline
- Use at little Regex as possible
- Do not add complexity only for backward compatibility unless the user explicitly asks for it.
- Do not hack your way to a solution. Understand the problem and find a proper solution. Many times, a better understanding of the grammar or the tree will help you find a better solution.
- For parser/analyzer behavior, follow the global **Extraction Contract (All Parsers)** in [CONTRIBUTING.md](CONTRIBUTING.md).
- I'm running on Windows, but I can't run the app locally, so I work with docker. If you suggest comamnds, specify them using Unix syntax, but don't offer to run them. Just tell me the command. I will run it and give you the output.