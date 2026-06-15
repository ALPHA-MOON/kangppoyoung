package com.policyfund.notices.service;

import com.policyfund.notices.dto.ContentBlock;
import com.policyfund.notices.dto.DiffBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * 블록 단위 LCS diff. 동등성은 ContentBlock(record)의 equals 로 판단한다:
 * TextBlock 은 text, ImageBlock 은 src+name 기준.
 * (이미지 콘텐츠 해시 동등성[발견 #11]은 자산 저장이 생기는 P2b 에서 src 를 콘텐츠 주소화하여 충족.)
 */
public final class BlockDiff {

    private BlockDiff() {}

    public static List<DiffBlock> diff(List<ContentBlock> oldBlocks, List<ContentBlock> newBlocks) {
        int n = oldBlocks.size();
        int m = newBlocks.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = oldBlocks.get(i).equals(newBlocks.get(j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<DiffBlock> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (oldBlocks.get(i).equals(newBlocks.get(j))) {
                out.add(new DiffBlock("same", newBlocks.get(j)));
                i++; j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                out.add(new DiffBlock("del", oldBlocks.get(i)));
                i++;
            } else {
                out.add(new DiffBlock("add", newBlocks.get(j)));
                j++;
            }
        }
        while (i < n) out.add(new DiffBlock("del", oldBlocks.get(i++)));
        while (j < m) out.add(new DiffBlock("add", newBlocks.get(j++)));
        return out;
    }
}
