import type {ComponentProps} from "react";

import {ButtonRoot} from "./button";

/* -------------------------------------------------------------------------------------------------
 * Compound Component
 * -----------------------------------------------------------------------------------------------*/
export const Button = Object.assign(ButtonRoot, {
  Root: ButtonRoot,
});

export type Button = {
  Props: ComponentProps<typeof ButtonRoot>;
  RootProps: ComponentProps<typeof ButtonRoot>;
};

/* -------------------------------------------------------------------------------------------------
 * Named Component
 * -----------------------------------------------------------------------------------------------*/
export {ButtonRoot};

export type {ButtonRootProps, ButtonVariants} from "./button";

/* -------------------------------------------------------------------------------------------------
 * Re-export utilities
 * -----------------------------------------------------------------------------------------------*/
export {buttonVariants} from "./button";
