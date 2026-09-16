package org.sterl.llmpeon.docslinter;

import java.util.List;

record NextIdsResult(
        List<NextIds> nextIds,
        int lintedDocCount,
        int skippedDocCount) {

    NextIdsResult {
        nextIds = List.copyOf(nextIds);
    }

    int docFileCount() {
        return lintedDocCount + skippedDocCount;
    }
}
