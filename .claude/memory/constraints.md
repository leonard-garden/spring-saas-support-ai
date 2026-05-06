---
name: Hard Constraints
description: Non-negotiable rules about scope, time, and quality for spring-saas-support-ai
type: project
---

# Hard Constraints

## Time constraints

- **7-day hard cap per milestone** — ship on Day 7 no matter what
- Skip features if running late; never skip the deadline
- Apply for jobs AFTER each milestone, not after v1.0.0
- One blog post per milestone (rough draft is fine)

## Scope — DO NOT ADD

- ❌ Mobile app / native iOS/Android SDK
- ❌ Voice interface
- ❌ Multi-language UI (English only)
- ❌ Advanced analytics dashboard
- ❌ Third-party integrations beyond Stripe
- ❌ Multiple LLM providers (Claude only until v1.0)
- ❌ Self-hosted LLM support

**Why:** Each addition delays job applications. Momentum matters more than features.

## Scope — OK to add if proven necessary

- ✅ Critical security fixes
- ✅ Bug fixes blocking demo
- ✅ Performance issues affecting live demo UX

## Quality minimums

- 60%+ coverage on service layer
- 100% coverage on tenant isolation paths
- All API endpoints in OpenAPI/Swagger
- All sensitive operations audit-logged
- Rate limiting enabled in production (Bucket4j)
- Health checks working before deploy

## Decision rule

> "I'm not sure" is NOT a blocker — ship and iterate.
> No pivots without a concrete technical blocker.
> Adjust scope based on data after Milestone 2, not speculation.
