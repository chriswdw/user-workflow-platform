package com.platform.ingestion.domain.ports.in;

import com.platform.ingestion.domain.model.IngestionResult;
import com.platform.ingestion.domain.model.RawInboundRecord;

public interface IIngestRecordUseCase {

    IngestionResult ingest(RawInboundRecord record);
}
