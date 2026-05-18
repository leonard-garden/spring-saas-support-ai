package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.document.chunk.DocumentChunkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentHashFilterTest {

    @Mock
    private DocumentChunkRepository chunkRepository;

    @InjectMocks
    private ContentHashFilter contentHashFilter;

    @Test
    void computeHash_sameInput_sameHash() {
        String text = "deterministic input text";
        assertThat(contentHashFilter.computeHash(text)).isEqualTo(contentHashFilter.computeHash(text));
    }

    @Test
    void computeHash_differentInputs_differentHashes() {
        assertThat(contentHashFilter.computeHash("text one"))
                .isNotEqualTo(contentHashFilter.computeHash("text two"));
    }

    @Test
    void isNew_repositoryReturnsFalse_returnsTrue() {
        UUID docId = UUID.randomUUID();
        String hash = "somehash";
        when(chunkRepository.existsByDocumentIdAndContentHash(docId, hash)).thenReturn(false);
        assertThat(contentHashFilter.isNew(docId, hash)).isTrue();
    }

    @Test
    void isNew_repositoryReturnsTrue_returnsFalse() {
        UUID docId = UUID.randomUUID();
        String hash = "somehash";
        when(chunkRepository.existsByDocumentIdAndContentHash(docId, hash)).thenReturn(true);
        assertThat(contentHashFilter.isNew(docId, hash)).isFalse();
    }
}
