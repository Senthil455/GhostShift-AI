package ai.ghostshift.workflow;

import ai.ghostshift.domain.Statuses;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The complete set of actions a workflow may execute. There is no dynamic
 * registration: a model cannot add an action, permission, or destination.
 */
@Singleton
public class ActionRegistry {
    private final Map<String, ActionDescriptor> actions = new LinkedHashMap<>();

    public ActionRegistry() {
        register(new ActionDescriptor("LIST_FILES", "List files", Statuses.RiskLevel.LOW, "files:read", false,
                "List files inside the registered folder. Populates the workflow's file set.",
                Map.of(
                        "recursive", ParamSpec.optionalBoolean("Read subfolders up to the app scan depth."),
                        "maxFiles", ParamSpec.optionalInteger("Maximum files to list.", 1, 1000)
                )));
        register(new ActionDescriptor("FILTER_FILES", "Filter files", Statuses.RiskLevel.LOW, "files:read", false,
                "Keep only files matching extensions and/or a file-name fragment.",
                Map.of(
                        "extensions", ParamSpec.optionalStringList("Extensions such as .log or .png."),
                        "nameContains", ParamSpec.optionalString("Case-insensitive file-name fragment.", 1, 120)
                )));
        register(new ActionDescriptor("CLASSIFY_FILES", "Classify files", Statuses.RiskLevel.LOW, "files:read", false,
                "Apply deterministic rules and, when enabled, ask the local model about ambiguous names.",
                Map.of("useAi", ParamSpec.optionalBoolean("Use the configured local model for ambiguous files."))));
        register(new ActionDescriptor("PROPOSE_DESTINATION", "Propose destinations", Statuses.RiskLevel.LOW, "files:read", false,
                "Compute destinations under the app-managed organizer folder. No path may come from the model.",
                Map.of()));
        register(new ActionDescriptor("PREVIEW_FILE_MOVE", "Preview file moves", Statuses.RiskLevel.LOW, "files:read", false,
                "Check boundaries and destination conflicts before asking for approval.",
                Map.of()));
        register(new ActionDescriptor("MOVE_FILE", "Move approved files", Statuses.RiskLevel.MODERATE, "files:write", true,
                "Move previewed files without overwriting and record recovery metadata for undo.",
                Map.of("maxFiles", ParamSpec.optionalInteger("Safety cap on files moved in this step.", 1, 500))));
        register(new ActionDescriptor("HASH_FILE", "Hash files", Statuses.RiskLevel.LOW, "files:read", false,
                "Compute SHA-256 hashes of the selected files and report exact duplicates.",
                Map.of()));
        register(new ActionDescriptor("READ_APPROVED_TEXT", "Read approved text", Statuses.RiskLevel.LOW, "files:read", false,
                "Read a bounded text file inside the registered folder for analysis.",
                Map.of(
                        "relativePath", ParamSpec.optionalString("File path relative to the registered folder.", 1, 1024),
                        "maxChars", ParamSpec.optionalInteger("Maximum characters returned to the workflow.", 1000, 1_000_000)
                )));
        register(new ActionDescriptor("ANALYZE_LOG", "Analyze log", Statuses.RiskLevel.LOW, "ai:local", false,
                "Parse the approved text as a log, group evidence, and optionally interpret it with the local model.",
                Map.of("useAi", ParamSpec.optionalBoolean("Use the local model for a diagnostic summary."))));
        register(new ActionDescriptor("GENERATE_REPORT", "Generate report", Statuses.RiskLevel.LOW, "reports:write", false,
                "Save a Markdown report in GhostShift's local report storage.",
                Map.of("title", ParamSpec.optionalString("Report title.", 1, 200))));
        register(new ActionDescriptor("REQUEST_USER_APPROVAL", "Request approval", Statuses.RiskLevel.LOW, "workflow:approve", false,
                "Pause the run and require an explicit human approval before continuing.",
                Map.of(
                        "title", ParamSpec.optionalString("Approval title.", 1, 200),
                        "explanation", ParamSpec.optionalString("What the user is approving.", 1, 1000)
                )));
    }

    private void register(ActionDescriptor descriptor) {
        actions.put(descriptor.type(), descriptor);
    }

    public boolean exists(String type) {
        return type != null && actions.containsKey(type.toUpperCase(Locale.ROOT));
    }

    public ActionDescriptor require(String type) {
        if (type == null || !actions.containsKey(type.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported action type: " + type);
        }
        return actions.get(type.toUpperCase(Locale.ROOT));
    }

    public Map<String, ActionDescriptor> all() {
        return Map.copyOf(actions);
    }

    public Set<String> allowedTypes() {
        return new LinkedHashSet<>(actions.keySet());
    }

    public List<String> validateParameters(String type, Map<String, Object> parameters) {
        ActionDescriptor descriptor = require(type);
        List<String> errors = new ArrayList<>();
        Map<String, Object> values = parameters == null ? Map.of() : parameters;
        for (String key : values.keySet()) {
            if (!descriptor.parameters().containsKey(key)) {
                errors.add("Unknown parameter '" + key + "' for " + descriptor.type() + ".");
            }
        }
        for (Map.Entry<String, ParamSpec> entry : descriptor.parameters().entrySet()) {
            if (!values.containsKey(entry.getKey())) {
                continue;
            }
            String error = entry.getValue().validate(entry.getKey(), values.get(entry.getKey()));
            if (error != null) {
                errors.add(error);
            }
        }
        return errors;
    }

    public String catalog() {
        StringBuilder builder = new StringBuilder();
        for (ActionDescriptor descriptor : actions.values()) {
            builder.append("- ").append(descriptor.type()).append(": ").append(descriptor.description());
            if (!descriptor.parameters().isEmpty()) {
                builder.append(" Parameters: {");
                boolean first = true;
                for (Map.Entry<String, ParamSpec> entry : descriptor.parameters().entrySet()) {
                    if (!first) {
                        builder.append(", ");
                    }
                    builder.append(entry.getKey()).append(": ").append(entry.getValue().type());
                    first = false;
                }
                builder.append("}.");
            }
            if (descriptor.requiresApproval()) {
                builder.append(" Requires human approval at execution time.");
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    public record ActionDescriptor(
            String type,
            String label,
            Statuses.RiskLevel risk,
            String permission,
            boolean requiresApproval,
            String description,
            Map<String, ParamSpec> parameters
    ) {
    }

    public record ParamSpec(String type, boolean required, int min, int max) {
        static ParamSpec optionalBoolean(String ignored) {
            return new ParamSpec("boolean", false, 0, 0);
        }

        static ParamSpec optionalInteger(String ignored, int min, int max) {
            return new ParamSpec("integer", false, min, max);
        }

        static ParamSpec optionalString(String ignored, int min, int max) {
            return new ParamSpec("string", false, min, max);
        }

        static ParamSpec optionalStringList(String ignored) {
            return new ParamSpec("string[]", false, 0, 30);
        }

        String validate(String key, Object value) {
            return switch (type) {
                case "boolean" -> value instanceof Boolean ? null : "'" + key + "' must be true or false.";
                case "integer" -> {
                    if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
                        yield "'" + key + "' must be a whole number.";
                    }
                    int actual = number.intValue();
                    if (actual < min || actual > max) {
                        yield "'" + key + "' must be between " + min + " and " + max + ".";
                    }
                    yield null;
                }
                case "string" -> {
                    if (!(value instanceof String text)) {
                        yield "'" + key + "' must be text.";
                    }
                    int length = text.length();
                    if (length < Math.max(1, min) || length > max) {
                        yield "'" + key + "' must be " + Math.max(1, min) + "–" + max + " characters.";
                    }
                    yield null;
                }
                case "string[]" -> {
                    if (!(value instanceof List<?> list)) {
                        yield "'" + key + "' must be a list of strings.";
                    }
                    if (list.size() > max) {
                        yield "'" + key + "' may contain at most " + max + " entries.";
                    }
                    for (Object item : list) {
                        if (!(item instanceof String text)) {
                            yield "Every entry in '" + key + "' must be text.";
                        }
                        if ("extensions".equals(key) && !text.matches("\\.[A-Za-z0-9._-]{1,20}")) {
                            yield "Each extension must start with a dot, for example .log.";
                        }
                    }
                    yield null;
                }
                default -> "Unsupported parameter type.";
            };
        }
    }
}
