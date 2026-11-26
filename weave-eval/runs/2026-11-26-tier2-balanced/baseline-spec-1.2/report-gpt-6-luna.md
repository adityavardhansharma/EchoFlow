# gpt-6-luna — tier 2

| Metric | Value | Bar (07 §10.3) |
|---|---|---|
| Parse success | 88.9 % | ≥ 95 % |
| Dropped statements / block | 0.11 | ≤ 0.5 |
| Render success | 88.9 % | ≥ 97 % |
| Task completion | 76.0 % | ≥ 85 % |
| Tokens / block | 103 | — |
| Over-use (controls) | 0.0 % | ≤ 10 % |
| Missing replies | 0 | — |

**Meets the tier 2 bar: no.**

| Task | Category | Blocks | Parse | Renders | Completed | Detail |
|---|---|---|---|---|---|---|
| calc_emi | calculators | 1 | ✓ | ✓ | ✓ | ok |
| calc_tip | calculators | 1 | ✓ | ✓ | ✓ | ok |
| calc_km_mi | calculators | 1 | ✓ | ✓ | ✓ | ok |
| calc_bmi | calculators | 1 | ✓ | ✓ | ✗ | expected 22.857 ± 0.05; saw [70.0, 175.0, 228571.43, 70.0, 175.0, 228571.43] |
| calc_compound | calculators | 1 | ✓ | ✓ | ✓ | ok |
| calc_fd | calculators | 1 | ✓ | ✓ | ✓ | ok |
| calc_temp | calculators | 1 | ✓ | ✓ | ✓ | ok |
| calc_discount | calculators | 1 | ✓ | ✓ | ✓ | ok |
| calc_fuel | calculators | 1 | ✓ | ✓ | ✓ | ok |
| calc_gst | calculators | 1 | ✓ | ✓ | ✓ | ok |
| data_expenses | data | 1 | ✓ | ✓ | ✓ | ok |
| data_delhi_temp | data | 1 | ✓ | ✓ | ✓ | ok |
| data_quarters | data | 1 | ✓ | ✓ | ✓ | ok |
| data_population | data | 1 | ✓ | ✓ | ✓ | ok |
| data_running | data | 1 | ✓ | ✓ | ✓ | ok |
| data_grades | data | 1 | ✓ | ✓ | ✓ | ok |
| data_visits | data | 1 | ✓ | ✓ | ✗ | expected 116.67 ± 0.1; saw [140000.0, 1200.0, 1500.0, 1350.0, 1800.0, 2100.0, 2600.0, 140000.0] \| expected 1400.0 ± 7.0; saw [140000.0, 1200.0, 1500.0, 1350.0, |
| data_electricity | data | 1 | ✓ | ✓ | ✓ | ok |
| data_portfolio | data | 1 | ✓ | ✓ | ✓ | ok |
| data_scores | data | 1 | ✓ | ✓ | ✓ | ok |
| plan_jaipur | planning | 0 | — | — | ✗ | no weave block |
| plan_workout | planning | 1 | ✓ | ✓ | ✓ | ok |
| plan_packing | planning | 1 | ✓ | ✓ | ✓ | ok |
| plan_study | planning | 0 | — | — | ✗ | no weave block |
| plan_budget | planning | 1 | ✓ | ✓ | ✓ | ok |
| plan_wedding | planning | 0 | — | — | ✗ | no weave block |
| plan_meals | planning | 0 | — | — | ✗ | no weave block |
| plan_routine | planning | 0 | — | — | ✗ | no weave block |
| plan_savings | planning | 1 | ✓ | ✓ | ✓ | ok |
| plan_morning | planning | 1 | ✓ | ✓ | ✓ | ok |
| learn_solar | learning | 1 | ✗ | ✗ | ✗ | no renderable root |
| learn_spanish | learning | 1 | ✓ | ✓ | ✓ | ok |
| learn_photo | learning | 1 | ✗ | ✗ | ✗ | no renderable root |
| learn_capitals | learning | 1 | ✗ | ✗ | ✗ | no renderable root |
| learn_tables | learning | 1 | ✓ | ✓ | ✓ | ok |
| learn_elements | learning | 1 | ✗ | ✗ | ✗ | no renderable root |
| learn_interest | learning | 1 | ✓ | ✓ | ✓ | ok |
| learn_python | learning | 1 | ✓ | ✓ | ✓ | ok |
| learn_freedom | learning | 1 | ✗ | ✗ | ✗ | no renderable root |
| learn_pythagoras | learning | 1 | ✓ | ✓ | ✓ | ok |
| cmp_phones | comparison | 1 | ✓ | ✓ | ✓ | ok |
| cmp_loans | comparison | 1 | ✓ | ✓ | ✓ | ok |
| cmp_mobile | comparison | 1 | ✓ | ✓ | ✓ | ok |
| cmp_ev | comparison | 1 | ✓ | ✓ | ✓ | ok |
| cmp_rent | comparison | 1 | ✓ | ✓ | ✓ | ok |
| cmp_laptops | comparison | 1 | ✓ | ✓ | ✓ | ok |
| cmp_fd_equity | comparison | 1 | ✓ | ✓ | ✓ | ok |
| cmp_storage | comparison | 1 | ✓ | ✓ | ✓ | ok |
| cmp_cities | comparison | 1 | ✓ | ✓ | ✓ | ok |
| cmp_cards | comparison | 1 | ✓ | ✓ | ✓ | ok |
| control_01 | control | 0 | — | — | — | plain text (correct) |
| control_02 | control | 0 | — | — | — | plain text (correct) |
| control_03 | control | 0 | — | — | — | plain text (correct) |
| control_04 | control | 0 | — | — | — | plain text (correct) |
| control_05 | control | 0 | — | — | — | plain text (correct) |
| control_06 | control | 0 | — | — | — | plain text (correct) |
| control_07 | control | 0 | — | — | — | plain text (correct) |
| control_08 | control | 0 | — | — | — | plain text (correct) |
| control_09 | control | 0 | — | — | — | plain text (correct) |
| control_10 | control | 0 | — | — | — | plain text (correct) |
| control_11 | control | 0 | — | — | — | plain text (correct) |
| control_12 | control | 0 | — | — | — | plain text (correct) |
| control_13 | control | 0 | — | — | — | plain text (correct) |
| control_14 | control | 0 | — | — | — | plain text (correct) |
| control_15 | control | 0 | — | — | — | plain text (correct) |
| control_16 | control | 0 | — | — | — | plain text (correct) |
| control_17 | control | 0 | — | — | — | plain text (correct) |
| control_18 | control | 0 | — | — | — | plain text (correct) |
| control_19 | control | 0 | — | — | — | plain text (correct) |
| control_20 | control | 0 | — | — | — | plain text (correct) |

## Diagnostics

**calc_km_mi**
- E202 Unknown function "dec". Did you mean "deg"?

**calc_bmi**
- E202 Unknown function "dec". Did you mean "deg"?

**calc_temp**
- E202 Unknown function "dec". Did you mean "deg"?

**data_quarters**
- E202 Unknown function "dec". Did you mean "deg"?

**data_running**
- E202 Unknown function "dec". Did you mean "deg"?

**data_scores**
- E202 Unknown function "dec". Did you mean "deg"?

**plan_workout**
- W407 "auto" isn't defined; treated as a plain word.

**learn_solar**
- E105 Line 11: unexpected "when".

**learn_photo**
- E105 Line 9: unexpected "when".

**learn_capitals**
- E105 Line 12: unexpected "when".

**learn_elements**
- E105 Line 7: unexpected "when".

**learn_freedom**
- E105 Line 10: unexpected "when".

**learn_pythagoras**
- E202 Unknown function "dec". Did you mean "deg"?

**cmp_phones**
- E205 Chart.x expects rec, got str.
- E205 Chart.y expects rec, got str.
