package org.sterl.llmpeon.docslinter;

import java.util.List;

record NextIdsResult(
        List<NextIds> nextIds,
        int lintedDocCount,
        int skippedDocCount,
        List<String> skippedDocs) {

    NextIdsResult {
        nextIds = List.copyOf(nextIds);
        skippedDocs = List.copyOf(skippedDocs);
    }

    int docFileCount() {
        return lintedDocCount + skippedDocCount;
    }
}
