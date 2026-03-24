"use client";

import type {ComponentProps} from "react";
import {useMemo} from "react";

interface ButtonVariants {
  size?: "sm" | "md" | "lg";
  variant?: "primary" | "secondary";
}

interface ButtonRootProps extends ComponentProps<"button">, ButtonVariants {
  fullWidth?: boolean;
  isDisabled?: boolean;
}

const buttonVariants = (props: ButtonVariants): string => {
  return `btn-${props.variant || "primary"}-${props.size || "md"}`;
};

const ButtonRoot = ({
  children,
  className,
  fullWidth,
  isDisabled,
  size,
  variant,
  ...rest
}: ButtonRootProps) => {
  const styles = useMemo(() => {
    return buttonVariants({size, variant});
  }, [size, variant]);

  return (
    <button
      className={className ? `${className} ${styles}` : styles}
      disabled={isDisabled}
      style={{width: fullWidth ? "100%" : undefined}}
      {...rest}
    >
      {children}
    </button>
  );
};

export {ButtonRoot, buttonVariants};
export type {ButtonRootProps, ButtonVariants};
