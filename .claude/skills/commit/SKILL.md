---
name: commit
description: "Create git commits following project conventions: Conventional Commits + Gitmoji with Unicode emojis. Use when: (1) user says 'commit', '/commit', 'create a commit', (2) user asks to commit changes, (3) user asks for a commit message, (4) after completing implementation work and user wants to commit."
---

# Commit

Create git commits following project conventions: Conventional Commits + Gitmoji.

## Format

```
<emoji> <type>(optional-scope): <description>
```

- Always use **actual Unicode emoji** (e.g. `✨`), never shortcodes (e.g. `:sparkles:`)
- Use backticks around class/entity names in descriptions (e.g. `` `BuildDwelling` ``)
- Never include Claude Code attribution (`Co-Authored-By`, etc.)

## Emoji-Type Mapping

| Emoji | Type                              | When to use                                         |
|-------|-----------------------------------|-----------------------------------------------------|
| ✨     | `feat`                            | New feature or slice implementation                 |
| 🐛    | `fix`                             | Bug fix                                             |
| ♻️    | `refactor`                        | Code refactoring (no behavior change)               |
| 🏗️   | `refactor(architecture)`          | Architectural restructuring                         |
| ✅     | `test`                            | Adding or updating tests                            |
| 🧪    | `chore(tests)`                    | Test infrastructure/configuration                   |
| 📦    | `build(deps)`                     | Dependency additions or upgrades                    |
| ⬆️    | `deps`                            | Dependency version upgrades                         |
| 🐳    | `chore(docker)`                   | Docker and container configuration                  |
| 👷    | `ci`                              | CI/CD pipeline changes                              |
| 📝    | `docs`                            | Documentation                                       |
| 🔧    | `config(scope)` or `chore(scope)` | Configuration changes                               |
| 🎉    | `chore`                           | Project initialization                              |
| 🔥    | `remove`                          | Removing code or files                              |
| 🤖    | `ai-agent(scope)`                 | AI coding agent config (skills, CLAUDE.md, prompts) |
| ✨     | `feat(mcp)`                       | MCP server features (tools, resources)              |
| 🧑‍💻 | `chore(dx)`                       | Developer experience improvements                   |
| 📸    | `chore`                           | Snapshotting configuration                          |
| 🪲    | `chore(debugging)`                | Debugging helpers (log levels, etc.)                |
| 🚀    | `feat(scripts)`                   | Deployment or utility scripts                       |

## Event Modeling Slice Commits

When implementing a slice from Event Modeling, use `✨ feat:` with bounded context, slice type, and flow:

```
✨ feat: <BoundedContext> | write slice: <CommandName> -> <EventName(s)>
✨ feat: <BoundedContext> | read slice: (<EventName(s)>) -> <ReadModelName>
✨ feat: <BoundedContext> | automation: <EventName> -> <CommandName>
✨ feat: <BoundedContext> | write slices: <CommandName1> and <CommandName2>
```

Examples:
```
✨ feat: Creature Recruitment | write slice: BuildDwelling -> `DwellingBuilt`
✨ feat: Creature Recruitment | write slice: RecruitCreature -> (`CreatureRecruited`, `AvailableCreaturesChanged`)
✨ feat: Creature Recruitment | read slice: (`CreatureRecruited`, `AvailableCreaturesChanged`) -> `GetAllDwellings`
✨ feat: Creature Recruitment | automation: `CreatureRecruited` -> `AddCreatureToArmy`
✨ feat: Astrologers | write slice: `ProclaimWeekSymbol` -> `WeekSymbolProclaimed`
✨ feat: Calendar | write slices: `StartDay` and `FinishDay`
```

## AI Agent vs AI Feature

```
# ai-agent = AI coding agents that help develop the project
🤖 ai-agent(claude-code): init with `CLAUDE.md`
🤖 ai-agent(skills): update AF5 slice documentation

# feat(mcp) = AI as a product feature in the app
✨ feat(mcp): add MCP server
✨ feat(mcp): `BuildDwelling` write slice | add MCP server tool
```

## Procedure

1. Run `git status` and `git diff --staged` (and `git diff` if needed) to understand changes
2. Run `git log --oneline -5` to see recent commit style
3. Draft a commit message following the format above
4. Stage relevant files (prefer specific files over `git add -A`)
5. Commit using HEREDOC format:
```bash
git commit -m "$(cat <<'EOF'
<emoji> <type>(scope): <description>
EOF
)"
```
6. Do NOT push. Only commit locally.
