package online.yudream.base.plugin.projectprogress.domain.valobj;

public record ProjectLocationEvidence(
        String address,
        Double latitude,
        Double longitude
) {

    public ProjectLocationEvidence {
        address = address == null ? "" : address.trim();
    }
}
