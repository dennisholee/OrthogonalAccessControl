# Product Requirements Document

## Product Name

Orthogonal Access Control Engine for Spring Boot Microservices

## Document Status

Draft v1.0

## 1. Overview

The Orthogonal Access Control Engine is a policy decision and policy enforcement platform for Java Spring Boot microservices. Its purpose is to externalize authorization logic from business services so that access control remains consistent, auditable, and adaptable across a distributed system.

This platform is defined as a centralized, decoupled Multi-Dimensional Authorization Platform powered by a graph-based authorization engine. The platform enforces fine-grained controls across independent structural boundaries: system channels (staff vs. customer), operational perimeters (markets and geographies), and product vectors (lines of business).

The engine must support three complementary authorization models:

- Policy-Based Access Control (PBAC) for centralized policy evaluation using contextual conditions.
- Role-Based Access Control (RBAC) for coarse-grained assignment of permissions through roles.
- Attribute-Assisted Relationship-Based Access Control (Attribute-Assisted ReBAC) for decisions based on graph relationships between actors and resources, refined by attributes such as markets, line of business, customer or staff channels, geography, or time.

The term orthogonal means authorization must be separated from business feature logic, reusable across services, and operable as a shared platform capability rather than reimplemented independently in each microservice.

### 1.1 Strategic Objective

Secure enterprise banking operations by ensuring principals can only access records matching both organizational attributes and explicit relationship assignments. The system must prevent data cross-contamination across regions and business units while maintaining sub-10 ms authorization evaluation latency for common decision paths.

## 2. Problem Statement

Spring Boot microservices often accumulate inconsistent authorization logic embedded in controllers, service classes, and repository filters. This leads to duplicated rules, weak auditability, policy drift across teams, and slow policy change cycles.

The platform needs a unified authorization engine that:

- Enforces consistent decisions across microservices.
- Allows product and security teams to evolve policies without widespread code changes.
- Supports enterprise-grade multi-tenant and hierarchical access patterns.
- Enables both synchronous decision checks and explainable audit trails.

## 3. Goals

- Provide a shared authorization engine usable by all Java Spring Boot microservices.
- Support PBAC, RBAC, and Attribute-Assisted ReBAC in a single decision model.
- Minimize authorization code inside business services.
- Deliver low-latency decisions suitable for online request flows.
- Provide policy lifecycle management, versioning, audit logs, and explainability.
- Support multi-tenant isolation and delegated administration.
- Integrate cleanly with Spring Security and common identity providers.
- Use a centralized graph authorization engine for relationship and permission evaluation with caveat-aware policy enforcement.

## 4. Non-Goals

- Replacing enterprise authentication or identity providers.
- Serving as a full workflow engine or business rules engine.
- Managing user provisioning as a source-of-truth IAM platform.
- Replacing database row-level security for every data store.
- Supporting non-Java runtimes in the first release.

## 5. Target Users

- Platform engineers integrating the engine into Spring Boot services.
- Security architects defining centralized access control policy.
- Application teams modeling roles, permissions, resources, and relationships.
- Compliance and audit teams reviewing decision history and policy changes.
- Tenant administrators managing scoped roles and delegated permissions.

## 6. Primary Use Cases

### 6.1 API Authorization

Determine whether a caller may perform an action such as read, create, update, approve, export, or delete on a resource exposed by a Spring Boot service.

### 6.2 Tenant-Scoped Administration

Allow tenant admins to manage users, roles, and resources only within their tenant boundaries.

### 6.3 Relationship-Based Access

Allow access when a subject has a relationship to the resource, such as owner, reviewer, manager, delegate, member, or project collaborator.

### 6.4 Policy Conditions

Restrict or grant access based on runtime attributes such as time-of-day, environment, IP range, client application, geography, data classification, resource status, risk score, or emergency override flags.

### 6.5 Explainable Decisions

Return machine-readable reasons for allow or deny results for audit, debugging, and support workflows.

## 7. Product Scope

### 7.1 In Scope

- Centralized decision service for authorization requests.
- Spring Boot integration library for policy enforcement points.
- Policy authoring, validation, versioning, publication, and rollback.
- Role and permission model with tenant scoping.
- Relationship graph support for subject-resource relationships.
- Attribute ingestion from identity, request context, and resource metadata.
- Decision audit trail and reporting.
- Caching and resilience patterns for low-latency evaluations.

### 7.2 Out of Scope for MVP

- Full graphical policy studio.
- Cross-language SDKs.
- Automatic policy mining from application code.
- Native graph visualization tooling.
- Fine-grained field masking or redaction enforcement at the UI layer.

## 8. Solution Vision

The product will provide a shared authorization plane composed of:

- An embeddable Spring Boot starter that intercepts application requests and issues authorization checks.
- A policy decision point (PDP) service that evaluates PBAC, RBAC, and Attribute-Assisted ReBAC rules.
- A policy administration point (PAP) for authoring and deploying policies.
- A policy information layer that resolves subject attributes, resource attributes, and relationship graph data.
- Audit and observability services for traceability and operational visibility.

## 9. High-Level Architecture

### 9.1 Deployment Model

The preferred initial deployment model is a centralized decision service with local enforcement in each Spring Boot microservice through a shared starter library.

Core components:

- Spring Boot Policy Enforcement Library
- Central Policy Decision Service
- Policy Store and Version Registry
- Relationship Store or Graph Adapter
- Attribute Resolver Layer
- Audit/Event Stream Publisher
- Admin API for policy and model management
- Authorization graph engine cluster for relationship tuples, permission checks, and lookup APIs

### 9.2 Trust and Identity Propagation Model

The platform must define a clear enterprise trust model for both user-driven and service-to-service traffic.

- End-user requests must carry authenticated user identity, tenant context, and delegated authorities from the enterprise identity provider.
- Service-to-service calls must use workload or service identities distinct from end-user identities.
- The platform must support propagation of caller context so downstream services can distinguish the originating user from the calling service.
- The engine must support delegated access, impersonation, and on-behalf-of patterns only through explicitly governed and auditable mechanisms.
- Every decision request must identify whether it is user-initiated, service-initiated, or delegated, and must preserve the full caller chain for audit.
- Trust boundaries between ingress gateways, Spring Boot microservices, the PDP, attribute resolvers, and relationship stores must be explicitly enforced with mutual authentication and signed or otherwise tamper-evident tokens.

### 9.3 Request Flow

1. A request enters a Spring Boot microservice.
2. The enforcement library extracts subject, action, resource, tenant, and request context.
3. The library resolves or enriches attributes needed for evaluation.
4. The authorization request is sent to the decision service or evaluated locally from a synchronized cache where allowed.
5. The decision engine evaluates RBAC grants, PBAC conditions, and ReBAC relationships under a deterministic precedence model.
6. The engine returns `allow`, `deny`, or `conditional deny` plus obligations and reasons.
7. The service enforces the result and emits audit events.

### 9.4 Spring Boot Integration Points

- Servlet filter or WebFlux filter
- Spring Security AuthorizationManager integration
- Method-level annotations for protected application services
- Feign or RestClient interceptors for service-to-service propagation
- Resource repository adapters for loading metadata when necessary

### 9.5 Regional Deployment and Continuity Model

- The runtime decision plane must support multi-region deployment for enterprise resilience.
- Policy distribution, relationship storage, and audit pipelines must define consistency and replication guarantees.
- The system must document acceptable degraded modes during regional failure, control-plane outage, and network partition conditions.
- Regional failover behavior must preserve default-deny semantics for protected operations unless an endpoint is explicitly approved for fail-open behavior.
- Critical policy bundles must be pre-positioned so protected services can continue enforcing the last known good policy set during temporary control-plane loss.

### 9.6 Authorization Data Matrix Definition

The engine must evaluate three orthogonal dimensions before authorizing any transaction or view request:

```text
          REGULATORY BOUNDARY
      Market (US/UK) + LoB (Retail)
               |
               v
          CHANNEL MODELING
         Staff Path | Customer Path
           |              |
           v              v
      Hierarchical Relationship  Direct Relationship
```

- Regulatory boundary constraints are enforced using contextual attributes such as market and line of business.
- Channel modeling differentiates staff journeys from customer journeys.
- Relationship checks evaluate either hierarchical access paths or direct assignments.

### 9.7 Authorization Schema and Caveat Requirements

The schema must prevent supernode traversal risk by using caveats for operational boundaries and tuples for structural relationships.

```zed
caveat compliance_matrix(
  user_market string,
  user_lob string,
  resource_market string,
  resource_lob string,
  is_suspended bool
) {
  user_market == resource_market &&
  user_lob == resource_lob &&
  !is_suspended
}

definition user {}

definition organizational_team {
  relation member: user
}

definition financial_asset {
  relation direct_owner: user
  relation assigned_team: organizational_team#member
  permission view_ledger = (direct_owner + assigned_team) with compliance_matrix
}
```

- Caveats must enforce market and line-of-business compliance at runtime.
- ReBAC tuples must encode channel-specific and team-specific access.
- Permissions must combine structural tuple relationships with caveat context.

## 10. Authorization Model

### 10.1 Core Decision Inputs

- Subject: user, service account, batch job, or system principal
- Action: verb such as read, write, approve, assign, delete, export
- Resource: domain object or API resource
- Environment: runtime context such as time, IP, region, network zone, risk, device
- Tenant: tenant or organizational boundary
- Relationships: graph edges between subject and resource or resource hierarchy

### 10.2 RBAC Requirements

- Roles must map to sets of permissions.
- Roles must support tenant-local scope.
- The system must support inherited roles and composite roles.
- Roles must be assignable to users, groups, and service principals.
- Separation-of-duties constraints should be supported for conflicting roles.

### 10.3 PBAC Requirements

- Policies must support boolean conditions over subject, resource, action, and environment attributes.
- Policies must support explicit deny rules.
- Policies must support policy sets and reusable policy fragments.
- Policies must support simulation and dry-run evaluation before publication.

### 10.4 Attribute-Assisted ReBAC Requirements

- Relationships must support direct and derived edges such as owner, member, manager-of, parent-of, delegated-to, and shared-with.
- Decisions must support traversals across bounded graph depth.
- Relationship evaluation must be combined with attribute predicates, for example `manager-of` and same-tenant, or `collaborator` and document classification below threshold.
- The model must support resource hierarchies so inherited relationships can apply to child resources where explicitly configured.
- The model must support channel-aware relationships so staff and customer paths can be constrained independently.

### 10.5 Decision Precedence

The engine must use a deterministic precedence strategy:

1. Explicit deny overrides allow.
2. Tenant boundary violations deny.
3. Required relationship conditions must be satisfied when a policy depends on ReBAC.
4. RBAC and PBAC grants may allow access when no higher-priority deny applies.
5. Missing mandatory attributes result in deny by default unless a policy explicitly defines fallback behavior.

### 10.6 Tenancy and Organizational Scoping Requirements

- The authorization model must support tenant, region, legal entity, market, line of business, and channel as first-class scoping dimensions.
- Policies must define whether a rule is global, tenant-scoped, business-unit-scoped, geography-scoped, or resource-local.
- Inheritance across organizational hierarchies must be explicit and configurable rather than implied.
- Cross-tenant or cross-geography access must require explicit policy allowance plus auditable justification.
- Delegated administration must be bounded by tenant and organizational scope so administrators cannot grant permissions outside their authority.
- The model must define conflict handling when multiple scopes apply, with narrower scope taking precedence unless an explicit global deny applies.

### 10.7 Service and Delegated Access Requirements

- The system must distinguish human principals, workload principals, batch principals, and delegated principals in policy evaluation.
- Service principals must never inherit end-user permissions implicitly.
- On-behalf-of access must require an explicit delegation token or equivalent trusted assertion and must be time-bounded.
- Impersonation capabilities must be restricted to approved administrative or support roles and must always generate high-signal audit events.
- Policies must be able to restrict service-to-service calls by calling service, destination service, environment, tenant scope, and approved operation set.

## 11. Functional Requirements

### 11.1 Policy Authoring and Management

- Provide APIs to create, update, validate, publish, deprecate, and roll back policies.
- Store all policies with version history and metadata including author, rationale, and effective date.
- Validate policies for syntax, semantic conflicts, unreachable rules, and missing attributes.
- Support scoped publication by environment and tenant where required.
- Require maker-checker approval for policy publication to production environments.
- Enforce separation of duties between policy authors, policy approvers, and tenant administrators.
- Support emergency break-glass policies with explicit expiry, approval trace, and post-event review.
- Support environment promotion workflows so policy bundles progress through development, test, staging, and production with recorded approvals.

### 11.2 Decision API

- Expose a synchronous API for single authorization decisions.
- Expose a bulk API for batch decisions where one user requests access to many resources.
- Return decision outcome, matched policy identifiers, explanation codes, obligations, and evaluation timestamp.
- Support a stable versioned contract with backward-compatible evolution rules.
- Return a structured error taxonomy that distinguishes input validation, resolver timeout, policy evaluation failure, dependency outage, and authorization denial.

### 11.2.1 FR-1 Real-Time Access Evaluation (Check API)

- FR-1.1: Expose a high-performance gRPC and REST authorization endpoint for `CheckPermission`.
- FR-1.2: Accept runtime context including `user_market`, `user_lob`, `resource_market`, `resource_lob`, and `session_security_flags`.
- FR-1.3: Return `DENY` mapped to HTTP 403 whenever contextual compliance fails, even when a structural relationship exists.

### 11.3 Spring Boot SDK

- Provide a starter with opinionated auto-configuration.
- Support annotations such as `@RequireAccess(action = "approve", resource = "invoice")`.
- Support programmatic authorization for non-HTTP flows.
- Integrate with Spring Security authentication principals and JWT claims.
- Support secure propagation of end-user and workload identity context in service-to-service calls.
- Expose configuration for endpoint-level fail-open or fail-closed behavior subject to policy and platform guardrails.

### 11.4 Attribute Resolution

- Resolve subject attributes from JWT, OIDC claims, identity directories, or user profile services.
- Resolve resource attributes from domain services or local adapters.
- Resolve environment attributes from request metadata and trusted infrastructure signals.
- Support pluggable resolvers with timeout budgets and fallback behavior.
- Support attribute classification, provenance tracking, freshness windows, and source precedence.
- Minimize retrieval of sensitive attributes to only those required by the active policy set.

### 11.5 Relationship Management

- Provide APIs to create, revoke, and query relationships.
- Support temporal relationships with start and end timestamps.
- Support imported relationships from upstream systems.
- Support idempotent writes and eventual consistency reconciliation.
- Support relationship lineage metadata so imported edges can be traced to upstream systems and reconciliation jobs.

### 11.5.1 FR-2 Transactional Consistency and Causal Safety

- FR-2.1: Eliminate replication-lag vulnerabilities for critical permission changes such as employee termination and account closure.
- FR-2.2: Use consistency tokens to propagate authorization state across upstream transaction boundaries and guarantee read-after-write causal consistency.

### 11.5.2 FR-3 Reverse Search and Resource Discovery (Lookup API)

- FR-3.1: Support bulk discovery queries using `LookupResources`.
- FR-3.2: For list operations, return only resource IDs satisfying Market, LoB, channel, and relationship constraints directly from the authorization graph engine without application-side filtering.

### 11.6 Audit and Explainability

- Log all decision requests and outcomes with correlation IDs.
- Record policy version, attribute snapshot references, and relationship evidence used in evaluation.
- Provide human-readable and machine-readable explanation output.
- Support audit export and retention policies.
- Preserve caller-chain context, delegation evidence, and impersonation markers in every relevant audit event.
- Store opaque references to sensitive attribute values where feasible instead of duplicating regulated data in logs.

### 11.7 Administration

- Support platform administrators for global policies.
- Support tenant administrators with scoped management capabilities.
- Support delegated administration with approval workflows in later phases.
- Provide role models for policy author, policy approver, tenant administrator, auditor, and break-glass operator.
- Enforce administrative scoping boundaries across tenant, geography, and organizational unit.

## 12. Non-Functional Requirements

### 12.1 Performance

- NFR-1.1: P95 latency for single-point `CheckPermission` requests must be below 5 ms.
- NFR-1.2: P99 latency for complex nested hierarchical graph evaluations must be below 15 ms.
- NFR-1.3: Local authorization cache hit ratio must exceed 95% under steady-state production workloads.

### 12.2 Availability and Resilience

- Decision service target availability: 99.95%.
- Enforcement libraries must support configurable fail-closed and fail-open modes by endpoint classification.
- Cached policy bundles should allow short-term degraded operation during transient control-plane outages.
- Policy publication and distribution must define maximum propagation delay and rollback time objectives.

### 12.3 Scalability

- Support at least 10,000 decision requests per second in the initial shared deployment profile.
- Support horizontal scaling of the decision service and stateless enforcement clients.

### 12.4 Security

- All policy and admin APIs must require strong authentication and authorization.
- Sensitive attributes must be protected in transit and at rest.
- Audit logs must be tamper-evident.
- The engine must support tenant data isolation and least privilege.
- All service-to-service traffic between enforcement points, PDP, PAP, attribute resolvers, and storage backends must use mutually authenticated channels.
- The platform must support cryptographic key rotation and secrets rotation without service interruption.
- NFR-2.1: Relationship storage must use an append-safe transactional ledger backend, such as CockroachDB or PostgreSQL with MVCC, to preserve immutable authorization history.

### 12.5 Compliance and Governance

- Preserve policy version history for compliance review.
- Provide evidence for why access was granted or denied.
- Support retention and deletion rules aligned with enterprise governance requirements.
- Maintain approval history and attestation records for production policy changes.
- Support periodic access-policy certification by designated control owners.
- NFR-2.2: Audit logs for permission changes must include structural identifiers and omit sensitive personal or transaction values.
- NFR-2.3: Decision logs must include the active authorization schema version and evaluated caveat context for compliance evidence.

### 12.6 Operability

- Expose metrics for decision latency, deny rates, cache hit rates, resolver failures, and policy publication events.
- Provide distributed tracing hooks for decision paths.
- Provide health checks for policy sync, graph connectivity, and resolver dependencies.

### 12.7 Disaster Recovery and Business Continuity

- Define recovery time objective (RTO) and recovery point objective (RPO) targets for the decision plane, policy store, relationship store, and audit pipeline.
- Support regional failover procedures that are regularly tested.
- Define how stale but signed policy bundles may be used during recovery scenarios.
- Ensure backup and restore procedures preserve policy integrity, approval metadata, and audit chain continuity.
- Multi-region replication strategy for the authorization datastore must be documented and tested against consistency and failover objectives.

### 12.8 Data Protection and Residency

- Classify attributes and decision evidence by sensitivity level and regulatory impact.
- Support data residency controls for tenant, geography, and legal-entity-specific deployments where required.
- Store only the minimum attribute set required for policy evaluation and post-decision evidence.
- Define retention schedules separately for policies, decision logs, relationship data, and sensitive attribute references.

### 12.9 API Lifecycle and Compatibility

- Public and internal APIs must have explicit versioning, deprecation, and backward-compatibility policies.
- Decision and administration APIs must define idempotency behavior for mutation requests.
- API contracts must define pagination, filtering, and audit query boundaries for enterprise-scale administration and reporting use cases.

## 13. Data Model Requirements

Core entities:

- Subject
- Role
- Permission
- Policy
- Policy Set
- Resource Type
- Resource Instance
- Relationship Edge
- Tenant
- Attribute Definition
- Decision Log

Each entity must include stable identifiers, lifecycle status, audit metadata, and tenant scope where applicable.

The model must also represent organizational scope metadata such as geography, market, legal entity, business unit, and channel so policies can evaluate enterprise boundaries consistently.

## 14. API Requirements

### 14.1 Decision API Example Contract

Request payload should include:

- subjectId
- subjectType
- callerChain
- delegationContext
- tenantId
- organizationScope
- action
- resourceType
- resourceId
- subjectAttributes
- resourceAttributes
- environmentAttributes
- requestId
- policyDecisionMode
- requestedObligations
- consistencyToken
- context.user_market
- context.user_lob
- context.resource_market
- context.resource_lob
- context.session_security_flags

Response payload should include:

- decision
- decisionCode
- matchedPolicies
- obligations
- explanation
- policyVersion
- correlationId
- evaluationMode
- attributeEvidenceRefs
- relationshipEvidenceRefs
- errors

### 14.2 Administration APIs

- Policy CRUD and publish APIs
- Role and permission assignment APIs
- Relationship management APIs
- Policy simulation APIs
- Audit query APIs
- Approval workflow APIs
- Policy promotion and rollback APIs
- Authorization schema migration and caveat version management APIs

### 14.3 API Contract Requirements

- All APIs must publish machine-readable schemas and semantic version identifiers.
- Mutation APIs must define idempotency keys or equivalent replay-protection mechanisms where retries are expected.
- APIs must return structured error responses with retryability guidance.
- Audit and administration APIs must support pagination, filtering, sorting, and time-bounded querying.
- Contract changes that affect Spring Boot client integration must provide a documented compatibility window.
- Define explicit contracts for `CheckPermission` and `LookupResources`, including token consistency expectations when `consistencyToken` is supplied.

## 15. User Stories

- As a platform engineer, I want to integrate authorization with a Spring Boot starter so that application teams do not rewrite authorization plumbing.
- As a security architect, I want to define policies centrally so that access rules are consistent across services.
- As a tenant admin, I want to assign scoped roles so that I can manage my tenant without affecting others.
- As a product team, I want to express collaborator and ownership relationships so that access can follow real business relationships.
- As an auditor, I want to inspect why a decision was made so that compliance evidence is available on demand.

## 16. Acceptance Criteria

- A Spring Boot service can protect an endpoint with the starter and receive authorization decisions without embedding custom rule logic.
- A policy author can publish RBAC, PBAC, and ReBAC-based rules through supported APIs.
- The engine correctly evaluates mixed-mode policies with deterministic precedence.
- Tenant boundary violations are denied regardless of lower-level allow rules.
- Audit output contains sufficient evidence to reconstruct a decision.
- Policy rollback restores the previous decision behavior without redeploying application services.
- Service-to-service authorization decisions preserve distinct end-user and workload identity context.
- Production policy publication requires approval evidence and preserves separation of duties.
- Regional failover and control-plane outage scenarios preserve defined authorization behavior within approved continuity modes.
- Decision logs avoid unnecessary storage of regulated attribute values while preserving decision explainability.

## 17. MVP Definition

The MVP should deliver:

- Spring Boot starter for synchronous HTTP and method-level authorization.
- Central decision service.
- Role, permission, policy, and relationship APIs.
- PBAC condition support for common attribute types.
- Attribute-Assisted ReBAC for direct and one-hop inherited relationships.
- Policy versioning, dry-run simulation, and audit logging.
- Basic dashboards for latency, deny rate, and policy publication health.
- Native graph authorization engine integration for `CheckPermission` and `LookupResources` with caveat-based compliance matrix enforcement.

## 18. Future Phases

- Embedded local PDP option for ultra-low-latency services.
- Graph database optimization for large-scale ReBAC traversals.
- Policy authoring UI and relationship visualization.
- Field-level obligations such as masking and redaction.
- Cross-language SDKs for non-Java services.
- Risk-adaptive access policies using external signals.

## 19. Risks and Mitigations

- Policy complexity may become hard to reason about.
  Mitigation: provide simulation, linting, templates, and decision explainability.
- Attribute freshness may impact decision correctness.
  Mitigation: define resolver SLAs, cache TTLs, and authoritative data-source precedence.
- Relationship graph growth may affect performance.
  Mitigation: bound traversal depth, precompute common edges, and partition by tenant.
- Service teams may bypass the platform with inline checks.
  Mitigation: provide simple integration, architecture standards, and review controls.

## 20. Dependencies

- Enterprise identity provider with OIDC or JWT support
- Spring Security integration standards
- Resource metadata sources in application domains
- Storage for policies, audit logs, and relationship data
- Observability platform for metrics, logs, and traces
- Enterprise key management and secrets management services
- Network trust infrastructure for mutual TLS or equivalent workload identity enforcement
- Authorization graph control plane and datastore platform (for example CockroachDB or PostgreSQL with MVCC)

## 21. Success Metrics

- 80% of new Spring Boot services onboarded to the shared authorization starter within two quarters.
- 60% reduction in service-local authorization code paths.
- 50% reduction in time required to implement cross-service policy changes.
- 90% of audit requests answered from system-generated decision evidence without manual reconstruction.
- Less than 1% decision failure rate caused by attribute or relationship resolution faults.

## 22. Open Questions

- Should the relationship store be implemented on a relational model first or on a graph-native store from day one?
- Which policy expression language should be adopted for PBAC evaluation?
- Which endpoints are allowed to operate in fail-open mode, if any?
- Which enterprise systems are authoritative for organizational hierarchy, geography, market, and channel metadata?
- What maximum policy propagation delay is acceptable for regulated business operations?
- Which identity management framework will provide canonical Market and LoB claims for caveat evaluation?
- Should the multi-region disaster recovery baseline standardize on a distributed SQL backend as the primary authorization datastore?
- What migration plan and tooling are required to convert existing relational RBAC tables into graph tuples and caveat context mappings?

## 23. Recommended Implementation Direction

For an initial Java Spring Boot ecosystem, the recommended approach is:

- Use Spring Security integration for local enforcement points.
- Expose the PDP as a dedicated internal microservice.
- Keep the PAP and policy registry separate from runtime decision execution.
- Start with a relational persistence model for roles, policies, and basic relationships, with an abstraction that allows later migration to a graph store.
- Design the policy model so RBAC, PBAC, and ReBAC resolve into a unified decision context and precedence pipeline.
- Adopt a controlled policy promotion process with approval gates and immutable audit history.
- Design for multi-region continuity from the outset, even if the first deployment is active-passive.

This approach minimizes early platform complexity while preserving a clean path toward larger-scale relationship evaluation and richer policy management.

## 24. Enterprise Policy Operating Model

### 24.1 Policy Governance Roles and RACI

The platform must enforce enterprise policy governance with clear ownership and approval authority.

Core roles:

- Policy Author: drafts and updates policies within assigned domain scope.
- Policy Approver: validates risk, compliance, and business alignment before promotion.
- Policy Owner: accountable for policy intent, lifecycle, and control evidence.
- Tenant Administrator: manages tenant-local assignments within bounded scope.
- Platform Security Administrator: maintains global guardrails and deny policies.
- Auditor: reviews immutable history, approvals, and decision evidence.

RACI expectations:

- Draft policy creation: Author (R), Policy Owner (A), Approver (C), Auditor (I).
- Production policy publication: Approver (R), Policy Owner (A), Author (C), Auditor (I).
- Emergency break-glass activation: Platform Security Administrator (R), Policy Owner (A), Approver (C), Auditor (I).
- Policy retirement/deprecation: Policy Owner (R/A), Author (C), Approver (C), Auditor (I).

### 24.2 Policy Domain Ownership and Scope Boundaries

- Each policy must declare a domain owner and a control owner.
- Domains must include at minimum market, line of business, channel, and tenant scope.
- Cross-domain policies require joint approval by all affected domain owners.
- Global deny guardrails remain centrally managed and cannot be overridden by tenant-local policies.

### 24.3 Release Gates for Policy Promotion

Policy promotion between environments must pass mandatory gates:

1. Schema and syntax validation passes.
2. Semantic conflict and precedence analysis passes.
3. Simulation coverage meets threshold for impacted resources and principal types.
4. Blast-radius classification is assigned (low, medium, high, critical).
5. Approval count and approver seniority meet policy criticality rules.
6. Rollback plan and rollback objective are attached.

For high and critical changes, staged rollout with monitoring checkpoints is required before full promotion.

### 24.4 Policy Certification and Attestation SLA

- Every active production policy must have a certification status and next certification date.
- Certification cadence:
  - Critical guardrail policies: every 90 days.
  - High-impact domain policies: every 180 days.
  - Standard policies: every 365 days.
- Policies past certification SLA must move to restricted state unless explicitly waived by control owners.
- Waivers must include expiry date, risk rationale, and compensating controls.

### 24.5 Policy Catalog and Naming Standard

The platform must maintain a searchable policy catalog with required metadata:

- Policy ID
- Name
- Version
- Effect (allow or deny)
- Domain and scope tags
- Owner and approver identities
- Created and effective timestamps
- Certification status
- Decommission target date when deprecated

Naming convention format:

`POL.<DOMAIN>.<RESOURCE>.<ACTION>.<EFFECT>.v<MAJOR>`

Examples:

- `POL.RETAIL.ACCOUNT.VIEW.ALLOW.v1`
- `POL.GLOBAL.PAYMENT.CROSSBORDER.DENY.v3`

### 24.6 Policy Dependency and Change Impact Controls

- Policy sets must declare dependencies on guardrail policies and shared caveat definitions.
- Promotion must fail if dependency versions are unresolved or incompatible.
- Change impact reports must enumerate affected resources, principals, and geographic or line-of-business domains.
- Policy diff artifacts must be retained for audit and incident review.

### 24.7 Delegated Administration Control Model

- Delegated administrators can manage assignments only within approved tenant and organizational boundaries.
- Delegated administrators cannot alter global guardrail or cross-tenant policies.
- High-risk delegated actions must require secondary approval and produce high-priority audit events.
- Delegated permissions must be time-bounded and reviewed periodically.

### 24.8 Enterprise Policy Management Acceptance Criteria

- No production policy can be activated without owner assignment, required approvals, and recorded release gate evidence.
- Certification SLA compliance remains above 98% for active production policies.
- All policy changes are traceable from draft through production with immutable audit records.
- Unauthorized cross-domain policy modifications are prevented by scope controls.