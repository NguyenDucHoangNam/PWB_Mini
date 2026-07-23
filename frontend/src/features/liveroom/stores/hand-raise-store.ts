"use client";

import { create } from "zustand";

interface HandRaiseState {
  raisedBy: Set<string>;
  raise: (userId: string) => void;
  lower: (userId: string) => void;
  clear: () => void;
}

export const useHandRaiseStore = create<HandRaiseState>((set) => ({
  raisedBy: new Set<string>(),
  raise: (userId) =>
    set((state) => {
      const next = new Set(state.raisedBy);
      next.add(userId);
      return { raisedBy: next };
    }),
  lower: (userId) =>
    set((state) => {
      const next = new Set(state.raisedBy);
      next.delete(userId);
      return { raisedBy: next };
    }),
  clear: () => set({ raisedBy: new Set<string>() }),
}));

export const HAND_RAISE_SELF_KEY = "__hand_raise__";
