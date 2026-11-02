package guru.interlis.documentation;

import java.util.*;

/** Checked catalogue. Sections reference other sections rather than expanding structures recursively. */
public record DocumentationDocument(String snapshotId, Map<String,String> sourceRevisions,
                                    DocumentationOptions options, List<Section> sections) {
    public DocumentationDocument {Objects.requireNonNull(snapshotId);sourceRevisions=Map.copyOf(sourceRevisions);Objects.requireNonNull(options);sections=List.copyOf(sections);}
    public record Section(String id,String name,String kind,boolean abstractType,String description,
                          String type,String base,List<Member> members,List<Member> inherited,List<String> constraints) {
        public Section {Objects.requireNonNull(id);Objects.requireNonNull(name);Objects.requireNonNull(kind);Objects.requireNonNull(description);Objects.requireNonNull(type);Objects.requireNonNull(base);members=List.copyOf(members);inherited=List.copyOf(inherited);constraints=List.copyOf(constraints);}
    }
    public record Member(String id,String name,String kind,String type,String cardinality,String origin,String description,List<String> references) {
        public Member {Objects.requireNonNull(id);Objects.requireNonNull(name);Objects.requireNonNull(kind);Objects.requireNonNull(type);Objects.requireNonNull(cardinality);Objects.requireNonNull(origin);Objects.requireNonNull(description);references=List.copyOf(references);}
    }
}
