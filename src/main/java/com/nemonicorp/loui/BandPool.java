package com.nemonicorp.loui;

import java.util.HashMap;
import java.util.Map;

/**
 * Controla quais faixas de altura estao ocupadas.
 *
 * Conta ocupantes por faixa: com o pool esgotado varios presos dividem a ultima,
 * e a faixa so volta a ficar livre quando o ultimo deles sai.
 */
public class BandPool {

    private final Map<Integer, Integer> occupants = new HashMap<>();

    /** Menor faixa livre. Com o pool esgotado, devolve a ultima (presos passam a dividi-la). */
    public int allocate(int max) {
        int limit = Math.max(1, max);
        for (int i = 0; i < limit; i++) {
            if (!occupants.containsKey(i)) {
                occupants.put(i, 1);
                return i;
            }
        }
        int last = limit - 1;
        occupants.merge(last, 1, Integer::sum);
        return last;
    }

    /** Marca como ocupada uma faixa lida do disco. */
    public void reserve(int band) {
        if (band < 0) return;
        occupants.merge(band, 1, Integer::sum);
    }

    /** Remove um ocupante. A faixa so fica livre quando o ultimo sai. */
    public void release(int band) {
        if (band < 0) return;
        Integer count = occupants.get(band);
        if (count == null) return;
        if (count <= 1) {
            occupants.remove(band);
        } else {
            occupants.put(band, count - 1);
        }
    }

    /** Quantos presos ocupam a faixa. */
    int occupantsOf(int band) {
        Integer count = occupants.get(band);
        return count == null ? 0 : count;
    }
}
