package com.platform.config.doubles;

import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.ports.out.IConfigDocumentWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryConfigDocumentWriter implements IConfigDocumentWriter {

    private final List<ConfigDocument> published = new ArrayList<>();

    @Override
    public void saveAll(List<ConfigDocument> documents) {
        published.addAll(documents);
    }

    public List<ConfigDocument> getAll() {
        return Collections.unmodifiableList(published);
    }
}
