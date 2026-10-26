import React, { createContext, useContext } from "react";
import { useCurrentFrame } from "remotion";

const OffsetContext = createContext(0);

export const AbsTime: React.FC<{ readonly offset: number; readonly children: React.ReactNode }> = ({
  offset,
  children,
}) => <OffsetContext.Provider value={offset}>{children}</OffsetContext.Provider>;

export const useAbsFrame = () => useCurrentFrame() + useContext(OffsetContext);
