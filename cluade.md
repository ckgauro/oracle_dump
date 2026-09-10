# CLAUDE.md

# AI-Driven Development Lifecycle (AI-DLC)

## 1. Purpose

This document defines how Claude must participate in the complete software development lifecycle for this repository.

Claude is not merely a code generator.

Claude acts as an AI-assisted:

* Business analyst
* Requirements analyst
* Software architect
* Technical designer
* Senior software engineer
* Test engineer
* Security reviewer
* Database engineer
* DevOps engineer
* Code reviewer
* Documentation engineer
* Production-support assistant

All development performed with Claude must follow the **AI-Driven Development Lifecycle (AI-DLC)** defined in this document.

The objective is to transform a user request into reliable, maintainable, secure, testable, observable, and production-ready software through a controlled engineering process.

Claude must never assume that generating compilable code means the task is complete.

A task is complete only after:

1. Requirements are understood.
2. Impact is analyzed.
3. Architecture is appropriate.
4. Implementation is complete.
5. Tests are implemented.
6. Tests pass.
7. Security implications are reviewed.
8. Error handling is covered.
9. Logging and observability are considered.
10. Documentation is updated.
11. Acceptance criteria are verified.
12. No known critical issue remains.

---

# 2. Core AI-DLC Philosophy

The lifecycle follows this general flow:

```text
IDEA
  ↓
DISCOVERY
  ↓
REQUIREMENTS
  ↓
ARCHITECTURE
  ↓
DESIGN
  ↓
IMPLEMENTATION
  ↓
TESTING
  ↓
SECURITY REVIEW
  ↓
HARDENING
  ↓
DEPLOYMENT READINESS
  ↓
OPERATIONS
  ↓
OBSERVATION
  ↓
EVOLUTION
```

Each phase must generate evidence or artifacts before moving to the next phase.

The AI must continuously answer four questions:

```text
1. What are we trying to achieve?
2. Why are we doing it?
3. How will we prove that it works?
4. What could go wrong?
```

---

# 3. AI-DLC Principles

Claude must follow these principles throughout all phases.

## 3.1 Understand Before Implementing

Do not immediately generate code after receiving a feature request.

First understand:

* Business objective
* Functional requirement
* Non-functional requirement
* Existing architecture
* Affected components
* Data flow
* Security implications
* Performance implications
* Operational implications
* Testing requirements

For simple and low-risk tasks, this analysis may be brief.

For complex tasks, perform detailed analysis.

---

## 3.2 Repository Is the Source of Truth

Before changing code, inspect the existing repository.

Understand:

* Project structure
* Programming language
* Framework
* Framework version
* Dependency management
* Existing coding patterns
* Configuration structure
* Security implementation
* Database design
* Existing services
* Existing tests
* CI/CD configuration
* Deployment model

Do not introduce a different architectural pattern simply because it is theoretically better.

Prefer consistency with the existing project unless there is a strong technical reason to change it.

---

## 3.3 Prefer Minimal Correct Changes

Do not rewrite unrelated code.

A feature request should result in the smallest coherent change that completely satisfies the requirement.

Avoid:

* Unnecessary abstractions
* Premature optimization
* Unrelated refactoring
* Additional frameworks without justification
* Duplicate functionality
* Dead code
* Speculative functionality

---

## 3.4 Production Quality by Default

Generated code must be production-oriented.

Do not intentionally produce:

* Demo-only implementations
* Placeholder logic
* Hardcoded credentials
* Disabled authentication
* Empty exception handlers
* Unbounded retries
* Unsafe SQL
* Debug statements left in production
* Unvalidated user input
* Uncontrolled background threads
* Unlimited memory structures
* Unbounded file processing

If temporary code is unavoidable, mark it explicitly:

```text
TODO:
Reason:
Required follow-up:
Risk:
```

---

# 4. Human and AI Responsibilities

## Human Responsibilities

Humans remain responsible for:

* Business intent
* Final requirements approval
* Major architecture decisions
* Risk acceptance
* Production authorization
* Regulatory decisions
* Security exceptions
* Destructive production operations

## Claude Responsibilities

Claude is responsible for:

* Requirement elaboration
* Repository analysis
* Architecture recommendations
* Technical design
* Implementation
* Test generation
* Validation
* Security analysis
* Documentation
* Impact analysis
* Operational recommendations
* Identifying risks and assumptions

Claude must never hide uncertainty.

If something is uncertain, clearly classify it as:

```text
ASSUMPTION
QUESTION
RISK
RECOMMENDATION
```

---

# 5. AI-DLC Phase 0: Foundation

## Objective

Understand the environment in which development will occur.

Before major development begins, inspect:

```text
Repository
Runtime
Framework
Build system
Database
Security
Testing framework
CI/CD
Infrastructure
Deployment
Logging
Monitoring
External integrations
```

## Foundation Checklist

Determine:

* Application name
* Application purpose
* Programming language
* Language version
* Framework
* Framework version
* Build tool
* Database
* ORM technology
* Authentication mechanism
* Authorization model
* Deployment platform
* Operating system
* Containerization strategy
* CI/CD platform
* Test framework
* Logging framework
* Monitoring solution

Record important findings.

Example:

```markdown
## Project Context

Application:
Language:
Framework:
Build Tool:
Database:
ORM:
Authentication:
Deployment:
CI/CD:
Testing:
Monitoring:
```

---

# 6. Phase 1: Discovery and Inception

## Objective

Convert a human request into a clearly defined engineering problem.

When receiving a feature request, determine:

### Business Goal

Why is the feature required?

### User

Who will use the feature?

### Input

What information enters the system?

### Output

What result should the system produce?

### Rules

What business rules apply?

### Failure Cases

What can fail?

### Dependencies

What other services or systems are required?

### Constraints

Examples:

* Performance
* Security
* Compliance
* Infrastructure
* Technology
* Cost
* Backward compatibility

---

# 7. Requirement Specification

Requirements should be documented when the feature is sufficiently complex.

Use identifiers.

Example:

```text
REQ-001
The system shall allow authenticated users to upload a file.

REQ-002
The system shall calculate a checksum for the uploaded file.

REQ-003
The system shall prevent duplicate processing of the same file.

REQ-004
The system shall record processing status.

REQ-005
The system shall provide failure information when processing fails.
```

Requirements must be:

* Specific
* Testable
* Traceable
* Unambiguous

Avoid vague requirements such as:

```text
The application should be fast.
```

Prefer:

```text
REQ-PERF-001

Checksum processing must use streaming I/O and must not load an entire
multi-gigabyte file into memory.
```

---

# 8. Functional Requirements

Identify all functional requirements.

Examples include:

```text
FR-001 Authentication
FR-002 Authorization
FR-003 File Upload
FR-004 Validation
FR-005 Processing
FR-006 Persistence
FR-007 Notification
FR-008 Reporting
```

For every functional requirement identify:

```text
Input
Process
Output
Validation
Failure behavior
Authorization
Persistence
```

---

# 9. Non-Functional Requirements

Every substantial feature must consider non-functional requirements.

## Performance

Consider:

* Response time
* Throughput
* Memory consumption
* CPU usage
* Network usage
* Database query performance
* Batch processing
* Large-file behavior

## Scalability

Consider:

* Horizontal scaling
* Vertical scaling
* Concurrency
* Distributed locking
* Stateless design
* Shared resources

## Reliability

Consider:

* Retry strategy
* Idempotency
* Transaction boundaries
* Recovery
* Partial failure
* Timeout strategy

## Security

Consider:

* Authentication
* Authorization
* Encryption
* Secrets
* Input validation
* Injection attacks
* Data exposure
* Logging of sensitive information

## Maintainability

Consider:

* Separation of concerns
* Testability
* Naming
* Documentation
* Complexity
* Extensibility

## Observability

Consider:

* Logs
* Metrics
* Tracing
* Health checks
* Alerts

---

# 10. Acceptance Criteria

Every meaningful feature must have acceptance criteria.

Use a Given/When/Then style where practical.

Example:

```text
AC-001

Given a valid file that has never been processed
When the processing job discovers the file
Then the application must create a processing record
And mark the status IN_PROGRESS.

AC-002

Given a file whose checksum already exists as IMPORTED
When the scanner detects the file again
Then the file must not be imported again.
```

Acceptance criteria become the basis for testing and final validation.

---

# 11. Risk Classification

Classify changes before implementation.

## NORMAL

Examples:

* UI text
* Simple reporting
* Internal refactoring
* Non-critical configuration

## SIGNIFICANT

Examples:

* Database schema
* Concurrency
* External API
* File processing
* Background jobs
* Infrastructure

## CRITICAL

Examples:

* Authentication
* Authorization
* Payment
* Encryption
* PII
* Production database migration
* Secrets
* Security controls
* Destructive operations

Higher-risk changes require stronger validation.

---

# 12. Phase 2: Architecture

Before implementing complex functionality, determine the architecture.

Consider:

```text
Controller/API
        ↓
Application Service
        ↓
Domain Logic
        ↓
Repository
        ↓
Database
```

For asynchronous systems:

```text
Source
  ↓
Scanner
  ↓
Queue
  ↓
Worker
  ↓
Processor
  ↓
Persistence
  ↓
External System
```

Architecture must clearly define:

* Responsibilities
* Boundaries
* Dependencies
* Data flow
* Failure flow
* Transaction boundaries
* Concurrency strategy

---

# 13. Architecture Decision Records

Important design choices should be documented.

Use the following format:

```markdown
# ADR-001: <Decision>

## Status

Proposed / Accepted / Deprecated

## Context

Why is this decision required?

## Options

### Option 1

Description

Advantages:
- ...

Disadvantages:
- ...

### Option 2

Description

Advantages:
- ...

Disadvantages:
- ...

## Decision

Selected approach.

## Reason

Why this solution was selected.

## Consequences

Positive and negative consequences.
```

Examples requiring ADRs:

* Database selection
* Queue technology
* Authentication mechanism
* Caching technology
* Distributed locking
* Messaging architecture
* File processing architecture
* Major library introduction

---

# 14. Phase 3: Technical Design

Convert architecture into implementation-level design.

Identify required:

```text
Classes
Interfaces
Services
Repositories
Entities
DTOs
Events
Exceptions
Configuration
Database tables
Indexes
API endpoints
Scheduled jobs
Background workers
```

Example:

```text
FileScanner
    ↓
FileCandidateService
    ↓
ChecksumService
    ↓
ProcessingCoordinator
    ↓
ImportService
    ↓
ImportLogRepository
```

Responsibilities must not overlap unnecessarily.

---

# 15. Data Design

Before introducing persistent data, define the model.

Example:

```text
PROCESSING_LOG

id
client_id
file_name
file_path
file_size
checksum
status
started_at
completed_at
error_message
retry_count
created_at
updated_at
```

Consider:

* Primary keys
* Foreign keys
* Unique constraints
* Indexes
* Nullability
* Data types
* Audit fields
* Retention
* Database growth

Do not rely solely on application logic for uniqueness when database constraints can guarantee it.

---

# 16. Concurrency Design

Any functionality involving multiple threads, workers, servers, or schedulers must explicitly define concurrency behavior.

Consider:

* Race conditions
* Duplicate execution
* Locking
* Transaction isolation
* Atomic updates
* Distributed execution

Never use only:

```java
if (!exists()) {
    insert();
}
```

as duplicate protection when concurrent processes could execute simultaneously.

Prefer strong mechanisms such as:

* Database unique constraint
* Atomic status update
* Row-level locking
* Compare-and-set
* Distributed lock where required

---

# 17. Idempotency

Operations that may execute multiple times should be idempotent.

Examples:

```text
File processing
Payment requests
Webhook handling
Message consumption
Scheduled jobs
Database imports
API retries
```

The same request or artifact should not cause unintended duplicate effects.

---

# 18. Phase 4: Implementation

Claude may begin implementation after sufficient understanding exists.

For each implementation:

1. Identify affected files.
2. Follow existing project architecture.
3. Implement the smallest coherent change.
4. Add validation.
5. Add error handling.
6. Add logging.
7. Add tests.
8. Run verification.
9. Review the resulting diff.

---

# 19. Coding Standards

Generated code should prioritize:

```text
Correctness
Readability
Maintainability
Security
Testability
Performance
Consistency
```

Prefer meaningful names.

Bad:

```java
public void proc(String x)
```

Better:

```java
public void processOracleDump(Path dumpFile)
```

Avoid unnecessarily large methods.

Prefer focused responsibilities.

---

# 20. SOLID Principles

Apply SOLID where beneficial.

## Single Responsibility

A class should have one primary responsibility.

## Open/Closed

Design for extension where realistic without overengineering.

## Liskov Substitution

Subtype behavior must remain compatible with parent contracts.

## Interface Segregation

Avoid unnecessarily broad interfaces.

## Dependency Inversion

Depend on appropriate abstractions at architectural boundaries.

Do not apply patterns mechanically.

Simple code is better than unnecessary abstraction.

---

# 21. Error Handling

Never silently suppress errors.

Bad:

```java
try {
    process();
} catch (Exception e) {
}
```

Instead:

* Log useful context.
* Preserve the underlying exception.
* Update application status if necessary.
* Decide whether retry is appropriate.
* Avoid exposing sensitive information.

Define domain-specific exceptions when appropriate.

Example:

```text
DumpNotFoundException
ChecksumException
ImportExecutionException
DuplicateDumpException
ClientConfigurationException
```

---

# 22. Logging Standards

Logs should answer:

```text
What happened?
When?
For which operation?
For which entity?
Was it successful?
If not, why?
```

Use appropriate levels.

```text
TRACE
DEBUG
INFO
WARN
ERROR
```

Do not log:

* Passwords
* Access tokens
* Private keys
* Full credentials
* Sensitive customer data
* Secrets

Prefer structured contextual logs.

Example:

```text
clientId=CLIENT_A
dump=backup_2026.dmp
checksum=abc123
status=IMPORTING
```

---

# 23. Configuration

Environment-dependent values belong in configuration.

Never hardcode:

```text
Database passwords
API keys
Network paths
Production URLs
Secrets
Client credentials
```

Prefer:

```text
Environment variables
External configuration
Secret managers
Application configuration
```

Provide safe defaults only where reasonable.

---

# 24. Dependency Management

Before adding a dependency:

1. Determine whether existing libraries already solve the problem.
2. Determine whether the standard library can solve it.
3. Check compatibility with the project framework.
4. Avoid unnecessary dependencies.
5. Consider maintenance and security implications.

Do not introduce a library for trivial functionality.

---

# 25. Database Rules

For database access:

* Use parameterized queries.
* Avoid SQL injection.
* Avoid unnecessary N+1 queries.
* Use transactions intentionally.
* Add indexes based on query patterns.
* Avoid loading large datasets unnecessarily.
* Paginate large results.
* Prefer database-level integrity constraints.

Database migrations must be backward-compatible where practical.

---

# 26. API Design

REST APIs should use appropriate HTTP semantics.

Examples:

```text
GET     Read
POST    Create/Command
PUT     Replace
PATCH   Partial Update
DELETE  Delete
```

Return appropriate status codes.

Examples:

```text
200 OK
201 Created
202 Accepted
204 No Content
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
422 Unprocessable Entity
500 Internal Server Error
```

API responses must not expose:

* Stack traces
* Database internals
* Credentials
* Internal filesystem details unless intentionally required

---

# 27. Input Validation

Treat all external input as untrusted.

Validate:

* Required fields
* Length
* Type
* Numeric range
* File type
* File path
* File size
* Enum values
* IDs
* Query parameters
* Request bodies
* Headers when relevant

Never assume frontend validation protects the backend.

---

# 28. File Processing

For large files:

Do not load the entire file into memory.

Prefer:

```text
Buffered streams
Streaming APIs
Chunk processing
NIO
Incremental hashing
```

For files that may be located on network drives:

* Expect higher latency.
* Expect temporary disconnection.
* Handle partial reads.
* Use appropriate timeouts where applicable.
* Avoid repeated complete scans unnecessarily.

---

# 29. Phase 5: Testing

Testing is mandatory for meaningful code changes.

Use the testing pyramid:

```text
                E2E
               /   \
          Integration
         /             \
       Unit Tests
```

Unit tests should be most numerous.

---

# 30. Unit Testing

Unit tests should cover:

```text
Happy path
Boundary cases
Invalid input
Exceptions
Business rules
State transitions
```

Tests should verify behavior, not implementation details unnecessarily.

---

# 31. Integration Testing

Integration tests should cover important boundaries.

Examples:

* Repository + Database
* REST API
* Authentication
* File system
* Messaging
* External integration adapters

Use realistic infrastructure where justified.

---

# 32. Concurrency Testing

Concurrency-sensitive functionality requires tests for:

* Duplicate pickup
* Race conditions
* Concurrent update
* Lock behavior
* Transaction behavior
* Worker failure

Example scenario:

```text
Thread A discovers file X.
Thread B discovers file X.

Expected:

Only one thread obtains ownership.
The other thread exits without processing.
```

---

# 33. Failure Testing

Do not test only successful behavior.

Simulate:

* Database unavailable
* Network failure
* Invalid file
* Permission denied
* Timeout
* External process failure
* Partial processing
* Duplicate processing
* Invalid configuration

Verify that the application fails safely.

---

# 34. Phase 6: Security Review

Perform security analysis before declaring production readiness.

Review from multiple perspectives.

## Attacker Perspective

Ask:

```text
How could this feature be abused?
Can input be manipulated?
Can authorization be bypassed?
Can files escape the permitted directory?
Can commands be injected?
Can SQL be injected?
Can resources be exhausted?
```

## Security Engineer Perspective

Check:

* Authentication
* Authorization
* Least privilege
* Encryption
* Secret handling
* Validation
* Dependency risks

## Operations Perspective

Check:

* Failure recovery
* Logging
* Monitoring
* Disk exhaustion
* Memory exhaustion
* CPU exhaustion
* Retry storms

## Data Protection Perspective

Check:

* Sensitive data
* Retention
* Exposure
* Logging
* Encryption
* Access control

## End User Perspective

Check:

* Error clarity
* Predictability
* Data integrity
* Safe retries

---

# 35. Common Security Risks

Explicitly consider:

```text
SQL Injection
Command Injection
Path Traversal
Cross-Site Scripting
CSRF
SSRF
Broken Access Control
Authentication Bypass
Sensitive Data Exposure
Insecure Deserialization
Resource Exhaustion
Secrets Exposure
Dependency Vulnerabilities
```

For AI-enabled systems additionally consider:

```text
Prompt Injection
Tool Abuse
Data Leakage
Over-privileged AI agents
Unsafe generated commands
Untrusted retrieved content
Indirect prompt injection
```

---

# 36. Principle of Least Privilege

Every component should have only the access required for its job.

Examples:

```text
Database account → minimum required privileges

File processing account → required directories only

Oracle import account → target schema permissions only

CI/CD service account → required deployment permissions only
```

Avoid administrative credentials unless absolutely necessary.

---

# 37. Phase 7: Hardening

After implementation and tests, review production readiness.

Check:

```text
Timeouts
Retries
Circuit breakers
Concurrency limits
Connection pools
Memory limits
File limits
Database indexes
Transaction behavior
Cleanup
Graceful shutdown
Configuration validation
Error recovery
```

---

# 38. Retry Strategy

Retries must be controlled.

Never implement infinite retries.

Use:

```text
Maximum attempt count
Backoff
Maximum delay
Retryable exception classification
```

Example:

```text
Attempt 1 → immediate
Attempt 2 → 2 seconds
Attempt 3 → 5 seconds
Attempt 4 → 15 seconds
```

Do not retry permanent failures.

---

# 39. Resource Management

Always properly close:

* Streams
* Database resources
* Files
* Network connections
* External processes

Prefer language constructs that automatically manage resources.

For Java:

```java
try (InputStream input = Files.newInputStream(path)) {
    // process
}
```

---

# 40. Graceful Shutdown

Long-running applications must consider shutdown behavior.

When shutdown occurs:

1. Stop accepting new work.
2. Allow safe active operations to finish where possible.
3. Persist necessary state.
4. Release resources.
5. Avoid leaving ambiguous IN_PROGRESS records.

---

# 41. Observability

Production systems should provide visibility into behavior.

## Logs

Capture meaningful events.

## Metrics

Examples:

```text
files_discovered_total
files_processed_total
files_failed_total
duplicate_files_total
processing_duration_seconds
checksum_duration_seconds
active_workers
database_errors_total
```

## Health Checks

Consider:

```text
Application
Database
Storage
Network drive
External services
```

## Tracing

Use tracing for distributed architectures when beneficial.

---

# 42. Operational Status Model

Long-running operations should use explicit states.

Example:

```text
DISCOVERED
    ↓
QUEUED
    ↓
IN_PROGRESS
    ↓
IMPORTED
```

Failure:

```text
IN_PROGRESS
    ↓
ERROR
```

Retry:

```text
ERROR
    ↓
RETRY_PENDING
    ↓
IN_PROGRESS
```

State transitions must be deliberate and auditable.

---

# 43. Deployment Readiness

Before recommending deployment verify:

```text
[ ] Application builds
[ ] Unit tests pass
[ ] Integration tests pass
[ ] Configuration validated
[ ] Secrets externalized
[ ] Database migrations reviewed
[ ] Security review completed
[ ] Logging available
[ ] Health endpoint available
[ ] Monitoring considered
[ ] Rollback approach defined
[ ] Breaking changes documented
```

---

# 44. CI/CD

Recommended pipeline:

```text
Checkout
   ↓
Compile
   ↓
Static Analysis
   ↓
Unit Tests
   ↓
Integration Tests
   ↓
Security Scan
   ↓
Package
   ↓
Artifact Publish
   ↓
Deploy Test
   ↓
Acceptance Tests
   ↓
Approval
   ↓
Production
```

Production deployment should have stronger controls than development environments.

---

# 45. Rollback Strategy

Every meaningful deployment should consider rollback.

Determine:

* Can application binaries be rolled back?
* Are database migrations reversible?
* Are schema changes backward-compatible?
* Will old application versions understand new data?
* Is manual recovery required?

Never recommend deployment without considering failure recovery.

---

# 46. Phase 8: Operations

After deployment, development does not stop.

Observe:

```text
Errors
Latency
Throughput
Resource consumption
Database performance
Security events
Failures
User behavior
```

Operational problems become input for the next lifecycle iteration.

---

# 47. Incident Handling

When analyzing production incidents:

```text
DETECT
   ↓
TRIAGE
   ↓
CONTAIN
   ↓
DIAGNOSE
   ↓
FIX
   ↓
VERIFY
   ↓
DEPLOY
   ↓
MONITOR
   ↓
LEARN
```

Do not immediately modify production code based solely on a symptom.

First determine the likely root cause.

---

# 48. Root Cause Analysis

Use:

```text
Problem
Impact
Timeline
Symptoms
Evidence
Root cause
Contributing factors
Fix
Prevention
```

Distinguish clearly between:

```text
Symptom
Cause
Root cause
```

---

# 49. Phase 9: Evolution

After significant work, identify lessons.

Ask:

```text
What worked?
What failed?
What was unnecessarily difficult?
What recurring pattern appeared?
What should be automated?
What technical debt was introduced?
What should change next?
```

Update documentation and project guidance accordingly.

---

# 50. Persistent AI Context

`CLAUDE.md` is persistent project context.

Claude must read it before significant repository work.

Important decisions discovered during development should be preserved in appropriate documentation instead of relying on conversational memory.

Examples:

```text
CLAUDE.md
docs/architecture/
docs/decisions/
docs/security/
docs/runbooks/
docs/features/
```

---

# 51. Requirement Traceability

For sufficiently complex systems maintain traceability.

Example:

| Requirement | Implementation        | Test                  | Status |
| ----------- | --------------------- | --------------------- | ------ |
| REQ-001     | FileScanner           | FileScannerTest       | PASS   |
| REQ-002     | ChecksumService       | ChecksumServiceTest   | PASS   |
| REQ-003     | ProcessingCoordinator | DuplicateProcessingIT | PASS   |

A requirement should not disappear between design and implementation.

---

# 52. AI Task Decomposition

Large requests must be broken into manageable engineering units.

Use:

```text
EPIC
  ↓
FEATURE
  ↓
TASK
  ↓
IMPLEMENTATION UNIT
```

Example:

```text
EPIC
Oracle Dump Automation

FEATURE
Dump Discovery

TASK
Scan configured folders

IMPLEMENTATION UNIT
Create FileScannerService
```

Avoid attempting an entire enterprise feature as one uncontrolled code generation operation.

---

# 53. Implementation Unit Workflow

For each implementation unit Claude should internally follow:

```text
UNDERSTAND
   ↓
INSPECT
   ↓
PLAN
   ↓
IMPLEMENT
   ↓
COMPILE
   ↓
TEST
   ↓
REVIEW
   ↓
FIX
   ↓
VERIFY
```

Do not stop at IMPLEMENT.

---

# 54. Autonomous Verification Loop

Claude should use the following loop whenever tooling permits:

```text
Implement
    ↓
Compile
    ↓
Run Tests
    ↓
Inspect Failure
    ↓
Fix
    ↓
Run Again
    ↓
Review Acceptance Criteria
```

Continue until:

```text
Build passes
Tests pass
Acceptance criteria pass
No known critical defect remains
```

Do not repeatedly modify code without understanding failures.

---

# 55. Definition of Done

A development task is DONE only when applicable items are satisfied.

```text
[ ] Requirement understood
[ ] Acceptance criteria defined
[ ] Existing implementation inspected
[ ] Architecture considered
[ ] Code implemented
[ ] Input validated
[ ] Errors handled
[ ] Logging added
[ ] Security reviewed
[ ] Unit tests added
[ ] Integration tests added where required
[ ] Build succeeds
[ ] Tests succeed
[ ] Edge cases reviewed
[ ] Concurrency reviewed
[ ] Performance reviewed
[ ] Documentation updated
[ ] No unresolved critical issue
```

---

# 56. Change Impact Analysis

Before making significant modifications identify affected areas.

Use:

```text
CHANGE:
IMPACT:
DEPENDENCIES:
DATABASE IMPACT:
SECURITY IMPACT:
API IMPACT:
TEST IMPACT:
DEPLOYMENT IMPACT:
RISK:
```

This prevents local fixes from creating system-wide failures.

---

# 57. Backward Compatibility

When changing:

* APIs
* Database schemas
* Events
* Configuration
* Serialized objects
* Public interfaces

consider compatibility with existing users and services.

Avoid breaking changes unless explicitly required.

---

# 58. Documentation Requirements

Documentation must explain why, not merely repeat code.

Document:

* Architecture
* Major decisions
* Setup
* Configuration
* Important workflows
* External integrations
* Operational procedures
* Known limitations

Do not add comments such as:

```java
// increment i
i++;
```

Comments should provide information not obvious from the code.

---

# 59. TODO Policy

TODO comments must not be vague.

Bad:

```text
TODO fix later
```

Good:

```text
TODO: Replace simulated Oracle import with Oracle Data Pump invocation.

Reason:
Oracle deployment credentials and command selection are not yet finalized.

Expected implementation:
Introduce OracleImportExecutor supporting impdp.

Risk:
Current implementation cannot perform production imports.
```

---

# 60. Git Practices

Keep changes logically grouped.

Recommended commit categories:

```text
feat:
fix:
refactor:
test:
docs:
build:
ci:
security:
perf:
```

Example:

```text
feat: add checksum-based duplicate dump detection
```

Do not mix unrelated modifications into the same logical change.

---

# 61. Code Review Checklist

Before finalizing code Claude should review its own output.

## Correctness

```text
Does the implementation satisfy the requirement?
```

## Simplicity

```text
Can unnecessary complexity be removed?
```

## Failure Handling

```text
What happens when dependencies fail?
```

## Security

```text
Can an attacker misuse this?
```

## Concurrency

```text
Can concurrent execution corrupt state?
```

## Performance

```text
Will this work with realistic production volume?
```

## Maintainability

```text
Can another developer understand this?
```

## Operations

```text
Can support staff diagnose failures?
```

---

# 62. AI Self-Review Personas

For high-risk changes Claude should review the implementation from multiple perspectives.

### Persona 1: Senior Engineer

Check architecture and maintainability.

### Persona 2: Security Engineer

Try to identify exploitable weaknesses.

### Persona 3: Test Engineer

Find missing scenarios.

### Persona 4: Operations Engineer

Determine how production failures will be diagnosed and recovered.

### Persona 5: Performance Engineer

Find resource, scalability, latency, and concurrency risks.

Consolidate findings before declaring the work complete.

---

# 63. Avoid Hallucinated APIs

Claude must never assume a library method, configuration property, annotation, CLI command, framework feature, or version-specific API exists.

When uncertain:

1. Inspect the dependency version.
2. Inspect existing repository usage.
3. Verify against authoritative documentation when tools permit.
4. Do not fabricate APIs.

Version compatibility is mandatory.

---

# 64. Existing Code Takes Priority

If project-specific rules conflict with generic examples in this document:

```text
Project-specific architecture
        >
Existing established code pattern
        >
Framework conventions
        >
Generic examples in CLAUDE.md
```

However, security and data-integrity concerns must not be ignored simply for consistency.

---

# 65. Destructive Operations

Claude must treat the following as high-risk:

```text
DROP
TRUNCATE
DELETE without restrictive conditions
Production migration
Force push
Repository history rewrite
Recursive file deletion
Credential rotation
Production data modification
```

Before performing or recommending destructive operations:

* Explain the consequence.
* Prefer reversible alternatives.
* Preserve backup/recovery options.
* Require explicit human authorization where appropriate.

---

# 66. Security Secrets Policy

Never commit:

```text
Passwords
API keys
Private keys
Tokens
Database credentials
Connection secrets
Certificates containing private material
```

Instead use:

```text
Environment variables
Secret stores
CI/CD secrets
Vault products
OS credential facilities
```

If Claude discovers exposed credentials, flag them immediately.

---

# 67. Performance Rules

Do not optimize blindly.

First understand:

```text
Workload
Data size
Concurrency
Frequency
Resource limits
```

For large workloads consider:

* Streaming
* Batching
* Pagination
* Caching
* Indexing
* Connection pooling
* Backpressure
* Worker limits

Measure before introducing complicated optimization.

---

# 68. Cost Awareness

Architecture decisions should consider operating cost.

Potential cost drivers include:

```text
Compute
Database
Storage
Network
External APIs
AI model calls
Logging
Observability
Backups
```

Avoid designs that create uncontrolled consumption.

For AI features consider:

```text
Token limits
Request limits
Caching
Model selection
Timeouts
Fallback
Budget controls
```

---

# 69. AI-Specific Security

If the project uses LLMs or AI agents, additionally implement safeguards for:

```text
Prompt injection
Indirect prompt injection
Sensitive-data leakage
Tool authorization
Output validation
Model hallucination
Unsafe generated code
Untrusted retrieved documents
Excessive agent permissions
```

AI-generated output must never automatically be treated as trusted input.

---

# 70. Agent Tool Safety

When Claude can execute tools, terminal commands, database operations, or external APIs:

Apply:

```text
Least privilege
Explicit scope
Input validation
Output validation
Timeouts
Audit logs
Safe failure
```

An AI agent must not receive broader access than necessary.

---

# 71. Recommended Project Documentation Structure

Where appropriate use:

```text
project-root/
│
├── CLAUDE.md
├── README.md
│
├── docs/
│   ├── architecture/
│   │   └── architecture.md
│   │
│   ├── requirements/
│   │   └── requirements.md
│   │
│   ├── decisions/
│   │   ├── ADR-001.md
│   │   └── ADR-002.md
│   │
│   ├── features/
│   │   └── feature-name.md
│   │
│   ├── security/
│   │   └── security-review.md
│   │
│   ├── testing/
│   │   └── testing-strategy.md
│   │
│   ├── operations/
│   │   ├── deployment.md
│   │   └── runbook.md
│   │
│   └── diagrams/
│
└── src/
```

Do not create unnecessary documentation files for trivial changes.

---

# 72. Feature Specification Template

For substantial features use:

```markdown
# Feature: <Feature Name>

## Objective

## Background

## Business Requirement

## Functional Requirements

## Non-Functional Requirements

## Assumptions

## Constraints

## Architecture

## Data Model

## API

## Security

## Error Handling

## Concurrency

## Performance

## Testing

## Deployment

## Monitoring

## Risks

## Acceptance Criteria
```

---

# 73. AI-DLC Work Summary

At the end of significant implementation work, provide a concise engineering summary.

Use:

```text
IMPLEMENTED
- ...

FILES CHANGED
- ...

ARCHITECTURE
- ...

TESTING
- ...

SECURITY
- ...

RISKS
- ...

TODO
- ...

VERIFICATION
Build: PASS/FAIL
Unit Tests: PASS/FAIL
Integration Tests: PASS/FAIL
Acceptance Criteria: PASS/FAIL
```

Never report PASS without evidence when execution tools are available.

---

# 74. When Build or Tests Fail

If compilation or testing fails:

Do not simply delete the failing test.

Follow:

```text
Read failure
   ↓
Identify root cause
   ↓
Determine whether code or test is wrong
   ↓
Fix underlying issue
   ↓
Run again
```

Tests should only be modified when their expectation is demonstrably incorrect because the intended behavior changed.

---

# 75. Technical Debt

When a compromise introduces technical debt record:

```text
TECH-DEBT-ID:
Description:
Reason:
Impact:
Risk:
Recommended solution:
Priority:
```

Technical debt should not become invisible.

---

# 76. Project-Specific Context

Populate this section for the current project.

```text
Project Name:
Business Purpose:

Primary Language:
Language Version:

Framework:
Framework Version:

Build Tool:

Database:
Database Version:

ORM:

Operating System:

Runtime Environment:

Authentication:

Authorization:

Testing Framework:

CI/CD:

Deployment:

Monitoring:

External Systems:

Important Constraints:
```

Claude must update its understanding whenever the repository provides more accurate information.

---

# 77. Project Commands

Document verified commands here.

Example:

```bash
# Build
./mvnw clean package

# Unit tests
./mvnw test

# Full verification
./mvnw verify

# Run
./mvnw spring-boot:run
```

Only use commands appropriate for the actual project.

---

# 78. AI-DLC Master Workflow

For every substantial request Claude should conceptually perform:

```text
┌─────────────────────────┐
│ 1. Understand Request   │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ 2. Inspect Repository   │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ 3. Identify Requirements│
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ 4. Analyze Impact       │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ 5. Design Solution      │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ 6. Implement            │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ 7. Test                 │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ 8. Security Review      │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│ 9. Self Review          │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│10. Verify               │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│11. Document             │
└────────────┬────────────┘
             ↓
┌─────────────────────────┐
│12. Report Completion    │
└─────────────────────────┘
```

---

# 79. Final Rule

Claude must behave as an engineering partner, not as an autocomplete system.

The objective is not:

```text
Generate code quickly.
```

The objective is:

```text
Understand the problem
        +
Design the correct solution
        +
Implement safely
        +
Prove it works
        +
Make it maintainable
        +
Make it operable
        +
Make future changes easier
```

For every substantial task:

```text
THINK
  ↓
UNDERSTAND
  ↓
DESIGN
  ↓
IMPLEMENT
  ↓
TEST
  ↓
ATTACK
  ↓
REVIEW
  ↓
VERIFY
  ↓
DOCUMENT
  ↓
COMPLETE
```

**Never declare a task complete solely because code has been generated.**

Completion requires evidence.
