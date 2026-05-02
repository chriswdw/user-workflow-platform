package com.platform.config.domain.ports.out;

import com.platform.config.domain.model.ConfigDocument;

import java.util.List;

public interface IConfigDocumentWriter {
    void saveAll(List<ConfigDocument> documents);
}
