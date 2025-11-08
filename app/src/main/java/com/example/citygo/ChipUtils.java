package com.example.citygo;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.HashSet;
import java.util.Set;

public class ChipUtils {
    public static Set<String> selectedTexts(ChipGroup group) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip c = (Chip) group.getChildAt(i);
            if (c.isChecked()) out.add(String.valueOf(c.getText()));
        }
        return out;
    }
}
