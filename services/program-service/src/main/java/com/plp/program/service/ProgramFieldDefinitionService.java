package com.plp.program.service;

import com.plp.program.model.dto.ProgramFieldDefinitionRequest;
import com.plp.program.model.dto.ProgramFieldDefinitionResponse;
import com.plp.program.model.entity.ProgramFieldDefinition;
import com.plp.program.model.enums.ProductType;
import com.plp.program.repository.ProgramFieldDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProgramFieldDefinitionService {

    private static final Set<String> VALID_TYPES = Set.of("TEXT", "NUMBER", "DROPDOWN");
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{1,99}$");

    /** LOS→PLP key renames applied when merging customFields into config. */
    public static final String LOS_MAX_INVOICE_VINTAGE = "maxInvoiceVintageDays";
    public static final String PLP_MAX_INVOICE_AGE = "maxInvoiceAgeDays";
    public static final String LOS_TENURE_DAYS = "tenureDays";
    public static final String PLP_MAX_TENURE = "maxTenureDays";

    private final ProgramFieldDefinitionRepository repository;

    @Transactional(readOnly = true)
    public List<ProgramFieldDefinition> activeDefinitionsForProduct(String productType) {
        List<ProgramFieldDefinition> active = repository.findByActiveTrueOrderBySortOrderAscLabelAsc();
        if (productType == null || productType.isBlank()) {
            return active;
        }
        return active.stream().filter(d -> appliesToProduct(d, productType)).toList();
    }

    @Transactional(readOnly = true)
    public List<ProgramFieldDefinitionResponse> list(String productType, boolean activeOnly) {
        List<ProgramFieldDefinition> rows = activeOnly
                ? repository.findByActiveTrueOrderBySortOrderAscLabelAsc()
                : repository.findAllByOrderBySortOrderAscLabelAsc();
        if (productType != null && !productType.isBlank()) {
            rows = rows.stream().filter(d -> appliesToProduct(d, productType)).toList();
        }
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProgramFieldDefinitionResponse create(ProgramFieldDefinitionRequest req) {
        String type = normalizeType(req.getInputType());
        String key = req.getFieldKey();
        if (key == null || key.isBlank()) {
            key = slugFromLabel(req.getLabel());
        } else {
            key = key.trim();
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "fieldKey must start with a letter and contain only letters, numbers, underscore");
        }
        if (repository.existsByFieldKey(key)) {
            throw new IllegalArgumentException("A program field with key already exists: " + key);
        }
        validateDropdown(type, req.getOptions());
        ProgramFieldDefinition entity = ProgramFieldDefinition.builder()
                .fieldKey(key)
                .label(req.getLabel().trim())
                .inputType(type)
                .optionsJson(copyOptions(req.getOptions()))
                .required(Boolean.TRUE.equals(req.getRequired()))
                .active(req.getActive() == null || Boolean.TRUE.equals(req.getActive()))
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 100)
                .productTypes(normalizeProductTypes(req.getProductTypes()))
                .systemManaged(false)
                .helpText(trimToNull(req.getHelpText()))
                .storageTarget("CUSTOM")
                .build();
        return toResponse(repository.save(entity));
    }

    @Transactional
    public ProgramFieldDefinitionResponse update(UUID id, ProgramFieldDefinitionRequest req) {
        ProgramFieldDefinition entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program field definition not found: " + id));
        String type = normalizeType(req.getInputType());
        validateDropdown(type, req.getOptions());
        if (!entity.isSystemManaged() && req.getFieldKey() != null && !req.getFieldKey().isBlank()) {
            String newKey = req.getFieldKey().trim();
            if (!newKey.equals(entity.getFieldKey())) {
                if (!KEY_PATTERN.matcher(newKey).matches()) {
                    throw new IllegalArgumentException("Invalid fieldKey");
                }
                if (repository.existsByFieldKey(newKey)) {
                    throw new IllegalArgumentException("fieldKey already exists: " + newKey);
                }
                entity.setFieldKey(newKey);
            }
        }
        entity.setLabel(req.getLabel().trim());
        entity.setInputType(type);
        entity.setOptionsJson(copyOptions(req.getOptions()));
        if (req.getRequired() != null) {
            entity.setRequired(req.getRequired());
        }
        if (req.getActive() != null) {
            entity.setActive(req.getActive());
        }
        if (req.getSortOrder() != null) {
            entity.setSortOrder(req.getSortOrder());
        }
        if (req.getProductTypes() != null) {
            entity.setProductTypes(normalizeProductTypes(req.getProductTypes()));
        }
        if (req.getHelpText() != null) {
            entity.setHelpText(trimToNull(req.getHelpText()));
        }
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        ProgramFieldDefinition entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program field definition not found: " + id));
        if (entity.isSystemManaged()) {
            throw new IllegalArgumentException("System-managed program fields cannot be deleted");
        }
        repository.delete(entity);
    }

    /**
     * Validate config values against active definitions. Required blanks fail.
     * Returns cleaned config (definitions keys coerced; other keys preserved).
     */
    public Map<String, Object> validateConfigAgainstDefinitions(ProductType productType, Map<String, Object> config) {
        String product = productType != null ? productType.name() : null;
        List<ProgramFieldDefinition> defs = activeDefinitionsForProduct(product);
        Map<String, Object> input = config != null ? new LinkedHashMap<>(config) : new LinkedHashMap<>();
        Map<String, Object> out = new LinkedHashMap<>(input);

        for (ProgramFieldDefinition def : defs) {
            String key = def.getFieldKey();
            Object v = input.get(key);
            // maxTenureDays may live on Program column only; skip required if only on column
            if (PLP_MAX_TENURE.equals(key)) {
                continue;
            }
            boolean blank = isBlankValue(v);
            if (blank) {
                if (def.isRequired()) {
                    throw new IllegalArgumentException(def.getLabel() + " is required");
                }
                continue;
            }
            out.put(key, coerceValue(def, v));
        }
        return out;
    }

    /** Translate LOS custom field keys into PLP config keys and dual-write maxTenureDays. */
    public static Map<String, Object> translateLosCustomFields(Map<String, Object> customFields) {
        if (customFields == null || customFields.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : customFields.entrySet()) {
            if (e.getKey() == null || isBlankValue(e.getValue())) {
                continue;
            }
            String key = e.getKey();
            Object value = e.getValue();
            if (LOS_MAX_INVOICE_VINTAGE.equals(key)) {
                out.put(PLP_MAX_INVOICE_AGE, value);
            } else if (LOS_TENURE_DAYS.equals(key)) {
                out.put(PLP_MAX_TENURE, value);
            } else {
                out.put(key, value);
            }
        }
        return out;
    }

    private Object coerceValue(ProgramFieldDefinition def, Object v) {
        String type = def.getInputType() != null ? def.getInputType().toUpperCase(Locale.ROOT) : "TEXT";
        return switch (type) {
            case "NUMBER" -> {
                if (v instanceof Number n) {
                    yield n.intValue() == n.doubleValue() ? n.intValue() : n.doubleValue();
                }
                String s = String.valueOf(v).trim();
                try {
                    if (s.contains(".")) {
                        yield Double.parseDouble(s);
                    }
                    yield Integer.parseInt(s);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(def.getLabel() + " must be a number");
                }
            }
            case "DROPDOWN" -> {
                String s = String.valueOf(v).trim();
                List<Map<String, Object>> options = def.getOptionsJson();
                if (options != null && !options.isEmpty()) {
                    boolean ok = options.stream().anyMatch(o ->
                            s.equalsIgnoreCase(String.valueOf(o.get("value"))));
                    if (!ok) {
                        throw new IllegalArgumentException(def.getLabel() + " has an invalid option");
                    }
                }
                yield s.toUpperCase(Locale.ROOT);
            }
            default -> String.valueOf(v).trim();
        };
    }

    private static boolean isBlankValue(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    private static boolean appliesToProduct(ProgramFieldDefinition d, String productType) {
        List<String> types = d.getProductTypes();
        if (types == null || types.isEmpty()) {
            return true;
        }
        String p = productType.trim().toUpperCase(Locale.ROOT);
        return types.stream().anyMatch(t -> p.equalsIgnoreCase(String.valueOf(t)));
    }

    private ProgramFieldDefinitionResponse toResponse(ProgramFieldDefinition d) {
        return ProgramFieldDefinitionResponse.builder()
                .id(d.getId())
                .fieldKey(d.getFieldKey())
                .label(d.getLabel())
                .inputType(d.getInputType())
                .options(d.getOptionsJson())
                .required(d.isRequired())
                .active(d.isActive())
                .sortOrder(d.getSortOrder())
                .productTypes(d.getProductTypes())
                .systemManaged(d.isSystemManaged())
                .helpText(d.getHelpText())
                .storageTarget(d.getStorageTarget())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    private static String normalizeType(String raw) {
        String t = raw != null ? raw.trim().toUpperCase(Locale.ROOT) : "";
        if (!VALID_TYPES.contains(t)) {
            throw new IllegalArgumentException("inputType must be TEXT, NUMBER, or DROPDOWN");
        }
        return t;
    }

    private static void validateDropdown(String type, List<Map<String, Object>> options) {
        if ("DROPDOWN".equals(type) && (options == null || options.isEmpty())) {
            throw new IllegalArgumentException("DROPDOWN fields require options");
        }
    }

    private static List<Map<String, Object>> copyOptions(List<Map<String, Object>> options) {
        if (options == null) {
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> o : options) {
            if (o != null) {
                out.add(new LinkedHashMap<>(o));
            }
        }
        return out;
    }

    private static List<String> normalizeProductTypes(List<String> productTypes) {
        if (productTypes == null || productTypes.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (String p : productTypes) {
            if (p != null && !p.isBlank()) {
                out.add(p.trim().toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static String slugFromLabel(String label) {
        if (label == null || label.isBlank()) {
            return "field" + System.currentTimeMillis();
        }
        String slug = label.trim()
                .replaceAll("[^a-zA-Z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", "_");
        if (slug.isEmpty() || !Character.isLetter(slug.charAt(0))) {
            slug = "f_" + slug;
        }
        if (slug.length() > 100) {
            slug = slug.substring(0, 100);
        }
        return slug;
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
