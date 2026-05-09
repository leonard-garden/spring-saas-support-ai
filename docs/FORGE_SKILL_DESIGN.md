# Forge Skill — Design Document

> Workflow skill: GitHub issue → implementation → PR

---

## Problem

Qua 4 tasks đầu (issues #1–#4), workflow từ nhận issue đến tạo PR có pattern nhất quán
nhưng dễ bỏ sót các bước quan trọng (commit trailers, test plan, architecture checks).
`forge` encode pattern này thành một skill invoke bằng `/forge #N`.

---

## Interface

```
/forge #N
```

Không có flag, không có mode. Claude đọc issue → tự judge complexity → chọn flow.
Claude **announce mode + lý do trước khi bắt đầu**. User có thể override.

---

## Auto Mode Selection

Claude đánh giá dựa vào issue content:

| Signal | → Mode |
|--------|--------|
| Config-only, 1–2 files, no business logic | QUICK |
| Multi-package, security-sensitive, cross-cutting concerns | FULL |
| New entity / service / repository layer | FULL |
| Design decisions cần reject alternatives | FULL |

**Ví dụ:**
- `#5 Auth Endpoints` → multi-endpoint, security → **FULL**
- `#9 OpenAPI Config` → 1–2 files, config only → **QUICK**

---

## Two Flows

### FULL — task phức tạp

```
Phase 1: Read & Analyze
Phase 2: Branch
Phase 3: Design               ← CHECKPOINT
Phase 4: Implement            ← sub-agent loop (implement → spec review → quality review)
Phase 5: Verification Loop    ← runtime + static checks với fresh evidence
Phase 6: Commit
Phase 7: PR
```

### QUICK — task đơn giản

```
Phase 1: Read & Analyze
Phase 2: Branch
Phase 3: Implement            ← inline (no sub-agent)
Phase 4: Verification Loop    ← same criteria, lighter static check
Phase 5: Commit
Phase 6: PR
```

Static checks vẫn bắt buộc trong QUICK — architecture violations không có exception.

---

## Phase Detail

### Phase 1 — Read & Analyze

Tools: `Bash` (`gh issue view #N`) + `Read` (CLAUDE.md, architecture.md, multi-tenancy.md)

Output:
- Scope và acceptance criteria của issue
- Constraints áp dụng (multi-tenancy rules, Java 21 idioms, test requirements)
- Sơ bộ scope-risk: narrow / moderate / broad

### Phase 2 — Branch

```bash
git checkout -b feature/issue-{N}-{slug}
```

Naming convention: `feature/issue-{N}-{short-description}`

### Phase 3 — Design (FULL only)

Agent: `oh-my-claudecode:planner` (model: opus)
Tools: `Glob`, `Grep` (codebase context)

Output trình bày cho user:
- Files sẽ tạo / sửa
- Key design decisions
- Alternatives đã consider và lý do reject
- Estimated scope-risk

**CHECKPOINT**: Dừng, chờ user confirm. User điều chỉnh → replanning. Không code trước khi có confirm.

### Phase 4 — Implement (Sub-agent Loop)

Mỗi task trong implementation chạy qua 3-agent pipeline:

```
┌─────────────────────────────────────────────────────────┐
│                  PER-TASK PIPELINE                      │
│                                                         │
│  1. Implementer (executor, fresh context)               │
│     └─ Implement + write tests + self-review            │
│     └─ Report: DONE | DONE_WITH_CONCERNS |              │
│               NEEDS_CONTEXT | BLOCKED                   │
│                                                         │
│  2. Spec Compliance Reviewer (code-reviewer, fresh)     │
│     └─ Does code match issue spec?                      │
│     └─ Anything extra built that wasn't asked?          │
│     ├─ ✅ PASS → tiếp tục                               │
│     └─ ❌ FAIL → implementer fixes → re-review          │
│                                                         │
│  3. Code Quality Reviewer (code-reviewer, fresh)        │
│     └─ Java 21 idioms, patterns, naming, structure      │
│     ├─ ✅ APPROVED → task done                          │
│     └─ ❌ Issues → implementer fixes → re-review        │
└─────────────────────────────────────────────────────────┘
```

**Quan trọng:** Spec compliance review phải pass trước khi chạy code quality review.

**Implementer status handling:**

| Status | Action |
|--------|--------|
| DONE | Tiến hành spec review |
| DONE_WITH_CONCERNS | Đọc concerns trước, assess xem có block không |
| NEEDS_CONTEXT | Cung cấp thêm context, re-dispatch |
| BLOCKED | Assess blocker → provide context / upgrade model / split task / escalate user |

Fresh sub-agent cho mỗi task — không reuse context, không inline fix.

### Phase 5 — Verification Loop

Sau implement, forge **không commit** cho đến khi tất cả 6 exit criteria đạt được.
Mọi claim phải có **fresh evidence** từ lệnh thực tế vừa chạy — không "should pass", không assume.

```
┌─────────────────────────────────────────────────────────┐
│                VERIFICATION LOOP                        │
│                                                         │
│  RUNTIME CHECKS                                         │
│  ─────────────────────────────────────────────────────  │
│  1. mvn test                    (run fresh, read output)│
│     ├─ exit 0, 0 failures → ✅                          │
│     └─ failures → fix (re-dispatch executor) → loop     │
│                                                         │
│  2. mvn verify                  (includes IT classes)   │
│     ├─ exit 0 → ✅                                      │
│     └─ failures → fix → loop                            │
│                                                         │
│  3. Code review                                         │
│     ├─ No CRITICAL/HIGH → ✅                            │
│     └─ Issues found → fix → loop                        │
│                                                         │
│  4. Coverage: mvn jacoco:report                         │
│     ├─ Service layer ≥ 60% → ✅                         │
│     └─ Below → thêm tests → loop                        │
│                                                         │
│  STATIC / ARCHITECTURE CHECKS                           │
│  ─────────────────────────────────────────────────────  │
│  5. Import compliance (Grep)                            │
│     └─ controller không import repository trực tiếp    │
│                                                         │
│  6. Architecture rules (Grep + AST + LSP diagnostics)  │
│     └─ Entity mới extend TenantEntity                   │
│     └─ @Async("taskExecutor") — không bare @Async       │
│     └─ disableFilter chỉ trong Admin* class             │
│     └─ Service không gọi EntityManager trực tiếp       │
│     └─ TenantContext.clear() trong finally block        │
│     └─ DTOs là record — không dùng class + Lombok       │
│     └─ Không return null trong service layer            │
└─────────────────────────────────────────────────────────┘
```

**Exit criteria** — phải đạt đủ cả 6:
- [ ] `mvn test` → exit 0, output đọc thực tế, 0 failures
- [ ] `mvn verify` → exit 0, output đọc thực tế
- [ ] Code review → No CRITICAL/HIGH (fresh review run)
- [ ] Coverage → service layer ≥ 60% (fresh jacoco report)
- [ ] Import compliance → Grep kết quả sạch
- [ ] Architecture rules → tất cả patterns check sạch

**Fix loop:** Re-dispatch fresh executor sub-agent với instructions cụ thể. Không inline fix để tránh context pollution.

**Max iterations:** 3 vòng. Sau 3 vòng vẫn fail → dừng, present blocker cụ thể cho user kèm evidence (output của lệnh fail).

### Phase 6 — Commit

```
Conventional commit subject line
<blank>
Body: WHY not WHAT — explain decision, constraint, tradeoff

Constraint: <active constraint that shaped this decision>
Rejected:   <alternative> | <reason>
Confidence: high | medium | low
Scope-risk: narrow | moderate | broad
Not-tested: <edge case not covered>
```

Trailers `Constraint` và `Rejected` là nơi capture design decisions — đọc `git log` sau 3 tháng vẫn rõ tại sao không chọn approach khác.

### Phase 7 — PR

```markdown
## Summary
- <bullet: what was built>
- <bullet: key decisions>

## Test plan
- [x] <test scenario> — <N/N tests pass>
- [x] No regressions — <total> tests pass
- [x] Architecture checks clean

Closes #{N}
```

---

## Agent & Tool Mapping

| Phase | Agent | Model | Tools |
|-------|-------|-------|-------|
| Read & Analyze | inline | — | `Bash` (gh), `Read` |
| Branch | inline | — | `Bash` (git) |
| Design | `oh-my-claudecode:planner` | opus | `Glob`, `Grep` |
| Implement (executor) | `oh-my-claudecode:executor` | opus/sonnet | `Write`, `Edit`, `Read` |
| Spec review | `oh-my-claudecode:code-reviewer` | opus | `Read`, `Glob` |
| Quality review | `oh-my-claudecode:code-reviewer` | opus | `Read`, `Grep` |
| Static checks | inline | — | `Grep`, LSP diagnostics, AST grep |
| Commit | inline | — | `Bash` (git) |
| PR | inline | — | `Bash` (gh) |

Model selection: opus cho complex/judgment tasks, sonnet cho mechanical implementation (1–2 files, clear spec).

---

## Risk & Mitigations

| Risk | Mitigation |
|------|------------|
| Context pollution trong implement | Fresh sub-agent per task — không reuse context |
| Mode selection sai | Claude announce mode + reason → user override ngay |
| Claim "tests pass" mà không chạy | Iron law: phải có fresh command output trước khi claim |
| Architecture violation detect muộn (nhiều files đã sửa) | Static check chạy song song với runtime check, không để sau cùng |
| Verification loop hết 3 iterations | Dừng, present blocker + evidence cụ thể — không escalate mơ hồ |
| Spec reviewer pass nhưng code quality chưa review | Hai stage luôn chạy theo thứ tự: spec compliance trước, quality sau |
| Bỏ sót Not-tested trailers | Commit template enforce — không để trống |

---

## Location

Skill file: `~/.claude/skills/forge.md` (global)

Commit trailers format và PR template là project-specific →
skill `@`-reference `CLAUDE.md` của project để lấy conventions.
