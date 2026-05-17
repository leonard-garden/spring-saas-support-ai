# SDLC Workflow Framework — Brainstorm Design

**Date:** 2026-05-13
**Topic:** Reusable software development workflow from idea to launch
**Scope:** SaaS / small-to-medium projects (reference: spring-saas-support-ai)
**Status:** Brainstorm — pending implementation plan

---

## Context & Goals

Định nghĩa một workflow framework chuẩn, reusable cho mọi milestone/feature từ khi có idea cho đến khi deploy production. Framework này:

- Áp dụng ngay cho project spring-saas-support-ai (bắt đầu với Admin UI cho M1)
- Có thể template hóa cho bất kỳ SaaS/web project nào
- Human-first: con người đảm nhiệm các role trước, AI hóa sau khi workflow đã được hiểu rõ

---

## Key Decisions

| Dimension | Decision |
|-----------|----------|
| Idea source | Internal (founder/engineer) + External (user feedback) |
| Launch definition | Deploy lên production và accessible |
| Role model | Hybrid — role rõ ràng, nhưng một người có thể đảm nhiệm nhiều role |
| Artifacts | Tùy phase — doc-only hoặc doc + prototype |
| Gates | Strict — stakeholder sign-off trước khi chuyển phase |
| Feedback loop | Severity-based: bug critical → fast-track, feature mới → full cycle |

---

## Role Definitions

| Role | Trách nhiệm chính |
|------|-------------------|
| **PO** (Product Owner) | Owns vision, prioritize backlog, sign-off feasibility & release |
| **PM** (Project Manager) | Timeline, resource, gate management, team coordination |
| **BA** (Business Analyst) | Requirements gathering, use cases, acceptance criteria |
| **Designer** | Wireframes, design system, UX flows |
| **Architect** | System design, API contract, technical decisions |
| **Engineer** | Code, unit test, code review, bug fix |
| **QA** | Test plan per story, task verification, regression, E2E |
| **DevOps** | CI/CD, infra, deploy, rollback |

> Với team nhỏ: một người đảm nhiệm nhiều role. Khi AI hóa: mỗi role là một agent với defined prompt.

---

## Workflow

```
[Idea/Milestone]
      ↓
 1. Kickoff
      ↓ [Gate: PO/PM sign-off]
 2. Feasibility
      ↓ [Gate: PO sign-off — làm hay bỏ]
 3. Discovery
      ↓ [Gate: PO + BA sign-off]
 4. Design
      ↓ [Gate: PO + Architect sign-off]
 5. Planning
      ↓ [Gate: PM + QA sign-off]
 6. Build (lặp per Story → per Task)
      ↓ [Gate: all stories QA-passed]
 7. QA / Staging
      ↓ [Gate: QA sign-off]
 8. Pre-release
      ↓ [Gate: PO + DevOps sign-off]
 9. Ship
      ↓
10. Post-release
```

---

## Phase Details

### 1. Kickoff
**Owner:** PM  
**Input:** Idea (internal hoặc user feedback)  
**Output:** Kickoff doc — scope, owner, timeline, success criteria  
**Gate:** PO + PM sign-off

### 2. Feasibility
**Owner:** PO + Architect  
**Input:** Kickoff doc  
**Output:** Feasibility report — technical feasibility, business value, effort estimate, go/no-go  
**Gate:** PO sign-off (làm hay bỏ)

### 3. Discovery
**Owner:** BA (+ PO, users)  
**Input:** Feasibility report  
**Output:** Requirements doc — user stories, use cases, edge cases, acceptance criteria  
**Gate:** PO + BA sign-off

### 4. Design
**Owner:** Designer + Architect  
**Input:** Requirements doc  
**Output:**
- Wireframes / mockups (Designer)
- Design system / component spec (Designer)
- API contract (Architect)
- Architecture decision (Architect)

**Gate:** PO + Architect sign-off

### 5. Planning
**Owner:** PM + Engineer + QA  
**Input:** Design artifacts  
**Output:**
- Epic → Story → Task breakdown
- Effort estimate per task
- QA test cases per Story (test plan)

**Gate:** PM + QA sign-off

### 6. Build
**Owner:** Engineer + QA  
**Loop: per Story → per Task**

```
[Story bắt đầu]
  └── QA confirm test cases cho story
        ↓
  [Task bắt đầu]
    ├── Engineer code + unit test
    ├── Tạo PR
    ├── Code review → approve
    ├── Merge PR
    └── QA test task vừa merge ← verify per task
        ↓
  [Story done] = all tasks passed QA
```

**Gate:** All stories done + QA-passed

### 7. QA / Staging
**Owner:** QA  
**Input:** All merged code trên staging branch  
**Output:**
- Regression test results
- E2E test results
- Bug list

**Loop:** Bug found → quay lại Build (fix + re-test)  
**Gate:** QA sign-off (zero critical bugs)

### 8. Pre-release
**Owner:** PM + DevOps + PO  
**Input:** QA sign-off  
**Output:**
- **Release notes** — thay đổi gì so với version trước
- **Config guide** — env vars, DB migration, infra changes cần thực hiện
- **Notify list** — team nào cần được thông báo (dev, ops, support, stakeholder)
- **Rollback plan** — nếu deploy fail thì revert thế nào

**Gate:** PO + DevOps sign-off

### 9. Ship
**Owner:** DevOps  
**Input:** Pre-release package  
**Steps:**
1. Run config changes (migration, env vars)
2. Deploy lên production
3. Smoke test trên prod
4. Notify teams

**Gate:** Smoke test passed

### 10. Post-release
**Owner:** PM + Engineer  
**Input:** Live production  
**Output:**
- Monitoring alerts configured
- User feedback collected
- Issues logged → feed vào Idea backlog

---

## Feedback Loop (Post-release)

| Scenario | Action |
|----------|--------|
| Critical bug | Fast-track: bỏ qua Kickoff/Feasibility/Discovery, vào thẳng Design/Build |
| Minor bug | Fast-track: vào thẳng Build |
| New feature request | Full cycle từ Kickoff |

---

## Artifact Map

| Phase | Artifact | Type |
|-------|----------|------|
| Kickoff | Kickoff doc | Document |
| Feasibility | Feasibility report | Document |
| Discovery | Requirements doc | Document |
| Design | Wireframes, Design system, API contract | Doc + Prototype |
| Planning | Epic/Story/Task list, QA test plan | Document |
| Build | PR, merged code, unit tests | Code |
| QA/Staging | Test results, bug list | Document |
| Pre-release | Release notes, config guide, rollback plan | Document |
| Ship | Deployed build | — |
| Post-release | Monitoring setup, feedback log | Document |

---

## AI-ready Notes

Khi chuyển sang AI hóa, mỗi role map sang một agent:

| Role | Agent Prompt Focus |
|------|-------------------|
| PO | Business value assessment, prioritization, sign-off criteria |
| PM | Timeline estimation, gate management, coordination |
| BA | Requirements extraction, use case generation, edge case identification |
| Designer | Wireframe generation, design system, component spec |
| Architect | API design, architecture decisions, technical feasibility |
| Engineer | Code generation, test writing, PR description |
| QA | Test case generation, verification, regression planning |
| DevOps | Deploy scripts, config management, rollback procedures |

Gates được thực hiện bởi orchestrator agent hoặc human review tùy ngưỡng confidence.

---

## First Application

**Milestone:** Admin UI cho M1 (spring-saas-support-ai)  
**Screens:** Auth (Login/Register), Dashboard, Members, Settings  
**Stack:** React + Tailwind CSS + shadcn/ui  
**Timeline:** ~2-3 ngày
