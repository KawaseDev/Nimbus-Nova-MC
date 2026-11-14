package cc.nimbusnova.mc.ship;

/** Small authored equipment models; no nutrition, plant-growth or medical calibration is claimed. */
public final class ShipActivities {
    private int galley, medical, science, cargo, complete;
    private int plants = 1 | 5 << 3 | 2 << 6;
    String feedback = "Inspect the equipment before committing shared supplies.";

    public boolean action(final ShipTask task, final int action) {
        if (completed(task)) return false;
        return switch (task) {
            case GALLEY -> ration(action);
            case PLANTS -> irrigate(action);
            case MEDICAL -> instrument(action);
            case SCIENCE -> classify(action);
            case STORAGE -> pack(action);
            default -> false;
        };
    }

    private boolean ration(final int action) {
        if (action < 0 || action > 6) return false;
        if (action == 6) {
            if (galley != (2 | 1 << 3 | 2 << 6)) {
                feedback = "The pouches need 2, 1 and 2 model units. Correct excess or missing water before sealing.";
                return true;
            }
            complete |= 1 << ShipTask.GALLEY.ordinal();
            feedback = "Ration pouches metered and sealed. Different allocations share one water budget.";
            return true;
        }
        final int pouch = action % 3, shift = pouch * 3, previous = galley >> shift & 7;
        final int next = previous + (action < 3 ? 1 : -1);
        if (next < 0 || next > 3) return false;
        galley = galley & ~(7 << shift) | next << shift;
        feedback = "Meter water into each pouch. Sealing an incorrect allocation would waste the crew's reserve.";
        return true;
    }

    private boolean irrigate(final int action) {
        if (action < 0 || action > 8) return false;
        if (action == 8) {
            for (int bed = 0; bed < 4; bed++) {
                final int moisture = plants >> bed * 3 & 7;
                if (moisture >= 3 && moisture <= 5) continue;
                feedback = "A bed is too dry or too wet. Keep every moisture reading between 3 and 5 before closing the loop.";
                return true;
            }
            complete |= 1 << ShipTask.PLANTS.ordinal();
            feedback = "The four beds have a safe model moisture range. Watering has a resource cost; crops do not grow instantly.";
            return true;
        }
        final int bed = action % 4, shift = bed * 3, moisture = plants >> shift & 7;
        if (action < 4) {
            final int neighborShift = (bed + 1) % 4 * 3, neighbor = plants >> neighborShift & 7;
            if (moisture == 7 && neighbor == 7) return false;
            plants = plants & ~(7 << shift) | Math.min(7, moisture + 2) << shift;
            plants = plants & ~(7 << neighborShift) | Math.min(7, neighbor + 1) << neighborShift;
            feedback = "A dose adds two units here and one in the next bed through the shared channel. Watch the wetter neighbors.";
        } else {
            if (moisture == 0) return false;
            plants = plants & ~(7 << shift) | (moisture - 1) << shift;
            feedback = "Excess water drained from this bed. Drainage does not create new shared drinking water.";
        }
        return true;
    }

    private boolean instrument(final int action) {
        if (action < 0 || action > 6) return false;
        if (action < 3) {
            if ((medical & 1 << action) != 0) return false;
            medical |= 1 << action;
            feedback = "Lead " + (action + 1) + " sampled. Compare instrument signal quality before spending a replacement kit.";
            return true;
        }
        if (action < 6) {
            if ((medical & 7) != 7) {
                feedback = "Sample all three sensor leads before choosing a repair. This checks equipment, not a medical diagnosis.";
                return true;
            }
            if (action != 4) {
                feedback = "That lead has a stable signal. Replace the low-quality lead instead of discarding working equipment.";
                return true;
            }
            if ((medical & 8) != 0) return false;
            medical |= 8;
            feedback = "Low-quality sensor lead replaced. Run the instrument self-test to verify the new reading.";
            return true;
        }
        if (medical != 15) {
            feedback = "The self-test needs three observations and the faulty sensor lead replaced first.";
            return true;
        }
        complete |= 1 << ShipTask.MEDICAL.ordinal();
        feedback = "Instrument self-test passed. The sensor check uses one shared kit; it does not diagnose or cure an astronaut.";
        return true;
    }

    private boolean classify(final int action) {
        if (action < 0 || action > 9) return false;
        if (action == 9) {
            if (science != (3 | 1 << 2 | 2 << 4)) {
                feedback = "Compare each record's two observations. Porous/nonmagnetic, dense/magnetic and layered samples belong in different groups.";
                return true;
            }
            complete |= 1 << ShipTask.SCIENCE.ordinal();
            feedback = "Three synthetic specimen records classified and archived from observations, not their display colors.";
            return true;
        }
        final int sample = action / 3, shift = sample * 2, classification = action % 3 + 1;
        if ((science >> shift & 3) == classification) return false;
        science = science & ~(3 << shift) | classification << shift;
        feedback = "Match the observations to ferrous, layered or porous records. Archive only after all three classifications agree.";
        return true;
    }

    private boolean pack(final int action) {
        if (action < 0 || action > 3) return false;
        if (action < 3) {
            final int shift = action * 2;
            cargo = cargo & ~(3 << shift) | ((cargo >> shift & 3) + 1 & 3) << shift;
            feedback = "Pack the bays within 8 mass units and balance the outer bays within 2. Kits and science compete for capacity.";
            return true;
        }
        final int left = cargo & 3, center = cargo >> 2 & 3, right = cargo >> 4 & 3;
        if ((left == 0 ? 0 : 1) + (center == 0 ? 0 : 1) + (right == 0 ? 0 : 1) < 2) {
            feedback = "Secure at least two cargo units. Empty bays cannot replace the supplies needed for the return.";
            return true;
        }
        if (mass(cargo) > 8 || Math.abs(weight(left) - weight(right)) > 2) {
            feedback = "Cargo exceeds the mass limit or is unbalanced. Redistribute the outer bays before securing the restraint.";
            return true;
        }
        complete |= 1 << ShipTask.STORAGE.ordinal();
        feedback = "Cargo balanced and restraints verified. Spare crates, medical kits and science instruments use different mass allowances.";
        return true;
    }

    public boolean completed(final ShipTask task) {
        return (complete & 1 << task.ordinal()) != 0;
    }

    public int board(final ShipTask task) {
        return switch (task) {
            case GALLEY -> galley;
            case PLANTS -> plants;
            case MEDICAL -> medical;
            case SCIENCE -> science;
            case STORAGE -> cargo;
            default -> 0;
        };
    }

    public int aux(final ShipTask task) {
        return switch (task) {
            case GALLEY -> 2 | 1 << 3 | 2 << 6;
            case PLANTS -> 3 | 5 << 3;
            case MEDICAL -> 72 | ((medical & 8) != 0 ? 92 : 17) << 7 | 69 << 14;
            case SCIENCE -> 3 | 1 << 2 | 2 << 4;
            case STORAGE -> mass(cargo) | 8 << 4;
            default -> 0;
        };
    }

    public static int mass(final int board) {
        return weight(board & 3) + weight(board >> 2 & 3) + weight(board >> 4 & 3);
    }

    private static int weight(final int type) {
        return type == 0 ? 0 : type + 1;
    }
}
