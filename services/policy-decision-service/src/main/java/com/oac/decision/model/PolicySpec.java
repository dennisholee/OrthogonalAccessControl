package com.oac.decision.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A structured, composable policy specification built from typed conditions.
 * <p>
 * This replaces the previous ad-hoc flat JSON document format with a clean
 * builder that enforces type safety and self-documenting policy intent.
 * <p>
 * Usage:
 * <pre>{@code
 * PolicySpec policy = PolicySpec.builder()
 *     .name("POL.REBAC.ACCOUNT.OWNER.READ.ALLOW.v1")
 *     .effect("ALLOW")
 *     .state(PolicyState.ACTIVE)
 *     .subjectType("human")
 *     .subjectId("user-reader")
 *     .action("read")
 *     .resourceType("account")
 *     .resourceId("acc-1")
 *     .boundaryContext(new BoundaryContext("tenant-a", "us", "retail", "cards", "staff"))
 *     .addCondition(PolicyCondition.spel("subject.department == 'compliance'"))
 *     .addCondition(PolicyCondition.rebac("manages", 3))
 *     .addCondition(PolicyCondition.timeWindow("09:00-17:00", "UTC"))
 *     .build();
 * }</pre>
 */
public class PolicySpec {

    private final String name;
    private final String effect;
    private final PolicyState state;
    private final String subjectType;
    private final String subjectId;
    private final String action;
    private final String resourceType;
    private final String resourceId;
    private final String tenant;
    private final String geography;
    private final String market;
    private final String lineOfBusiness;
    private final String channel;
    private final List<PolicyCondition> conditions;

    private PolicySpec(Builder builder) {
        this.name = builder.name;
        this.effect = builder.effect;
        this.state = builder.state;
        this.subjectType = builder.subjectType;
        this.subjectId = builder.subjectId;
        this.action = builder.action;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.tenant = builder.tenant;
        this.geography = builder.geography;
        this.market = builder.market;
        this.lineOfBusiness = builder.lineOfBusiness;
        this.channel = builder.channel;
        this.conditions = Collections.unmodifiableList(new ArrayList<>(builder.conditions));
    }

    // Getters
    public String name() { return name; }
    public String effect() { return effect; }
    public PolicyState state() { return state; }
    public String subjectType() { return subjectType; }
    public String subjectId() { return subjectId; }
    public String action() { return action; }
    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
    public String tenant() { return tenant; }
    public String geography() { return geography; }
    public String market() { return market; }
    public String lineOfBusiness() { return lineOfBusiness; }
    public String channel() { return channel; }
    public List<PolicyCondition> conditions() { return conditions; }

    /**
     * Converts this spec to the MongoDB document representation.
     * The document uses the new {@code conditions[]} array format,
     * making it structurally self-documenting.
     */
    public java.util.Map<String, Object> toDocument() {
        java.util.Map<String, Object> doc = new java.util.LinkedHashMap<>();
        doc.put("name", name);
        doc.put("effect", effect);
        doc.put("state", state != null ? state.name() : "ACTIVE");
        if (subjectType != null) doc.put("subjectType", subjectType);
        if (subjectId != null) doc.put("subjectId", subjectId);
        if (action != null) doc.put("action", action);
        if (resourceType != null) doc.put("resourceType", resourceType);
        if (resourceId != null) doc.put("resourceId", resourceId);
        if (tenant != null || geography != null || market != null || lineOfBusiness != null || channel != null) {
            java.util.Map<String, Object> bc = new java.util.LinkedHashMap<>();
            if (tenant != null) bc.put("tenant", tenant);
            if (geography != null) bc.put("geography", geography);
            if (market != null) bc.put("market", market);
            if (lineOfBusiness != null) bc.put("lineOfBusiness", lineOfBusiness);
            if (channel != null) bc.put("channel", channel);
            doc.put("boundaryContext", bc);
        }
        // Write conditions as an array of typed objects
        if (!conditions.isEmpty()) {
            List<java.util.Map<String, Object>> condList = new ArrayList<>();
            for (PolicyCondition cond : conditions) {
                java.util.Map<String, Object> condDoc = new java.util.LinkedHashMap<>();
                condDoc.put("type", cond.type());
                condDoc.put("params", new java.util.LinkedHashMap<>(cond.params()));
                condList.add(condDoc);
            }
            doc.put("conditions", condList);
        }
        return doc;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String effect;
        private PolicyState state = PolicyState.DRAFT;
        private String subjectType;
        private String subjectId;
        private String action;
        private String resourceType;
        private String resourceId;
        private String tenant;
        private String geography;
        private String market;
        private String lineOfBusiness;
        private String channel;
        private final List<PolicyCondition> conditions = new ArrayList<>();

        public Builder name(String name) { this.name = name; return this; }
        public Builder effect(String effect) { this.effect = effect; return this; }
        public Builder state(PolicyState state) { this.state = state; return this; }
        public Builder subjectType(String subjectType) { this.subjectType = subjectType; return this; }
        public Builder subjectId(String subjectId) { this.subjectId = subjectId; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder resourceType(String resourceType) { this.resourceType = resourceType; return this; }
        public Builder resourceId(String resourceId) { this.resourceId = resourceId; return this; }

        public Builder boundaryContext(String tenant, String geography, String market,
                                       String lineOfBusiness, String channel) {
            this.tenant = tenant;
            this.geography = geography;
            this.market = market;
            this.lineOfBusiness = lineOfBusiness;
            this.channel = channel;
            return this;
        }

        public Builder boundaryContext(BoundaryContext bc) {
            if (bc != null) {
                this.tenant = bc.tenant();
                this.geography = bc.geography();
                this.market = bc.market();
                this.lineOfBusiness = bc.lineOfBusiness();
                this.channel = bc.channel();
            }
            return this;
        }

        public Builder addCondition(PolicyCondition condition) {
            Objects.requireNonNull(condition, "condition must not be null");
            this.conditions.add(condition);
            return this;
        }

        public Builder addConditions(List<PolicyCondition> conditions) {
            conditions.forEach(c -> Objects.requireNonNull(c, "condition must not be null"));
            this.conditions.addAll(conditions);
            return this;
        }

        public PolicySpec build() {
            Objects.requireNonNull(name, "name is required");
            Objects.requireNonNull(effect, "effect is required");
            return new PolicySpec(this);
        }
    }
}