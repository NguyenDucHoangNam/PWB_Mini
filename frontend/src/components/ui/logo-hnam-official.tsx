import React from "react";

interface LogoHnamOfficialProps extends React.SVGProps<SVGSVGElement> {
  className?: string;
}

export function LogoHnamOfficial({ className, ...props }: LogoHnamOfficialProps) {
  return (
    <svg
      viewBox="0 0 410 110"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      {...props}
    >
      {/* Outer border rectangle */}
      <rect
        x="5"
        y="5"
        width="400"
        height="100"
        stroke="currentColor"
        strokeWidth="6"
        fill="none"
      />
      {/* Centered HNAM logo elements */}
      <g transform="translate(17.5, 5)">
        <path
          d="M 10 10 L 26 10 L 26 38 L 66 38 L 66 10 L 82 10 L 82 80 L 66 80 L 66 52 L 26 52 L 26 80 L 10 80 Z"
          fill="currentColor"
        />
        <rect x="100" y="10" width="14" height="70" fill="currentColor" />
        <rect x="156" y="10" width="14" height="70" fill="currentColor" />
        <polygon points="100,10 118,10 170,80 152,80" fill="currentColor" />
        <g transform="translate(172.25, -18.2) scale(1.155)">
          <defs>
            <polygon id="penrose-branch-off" points="20,85 85,85 53,30 43,36 64,72 13,72" />
          </defs>
          <use href="#penrose-branch-off" fill="currentColor" />
          <use href="#penrose-branch-off" fill="currentColor" transform="rotate(120 50 64.8)" />
          <use href="#penrose-branch-off" fill="currentColor" transform="rotate(240 50 64.8)" />
        </g>
        <path
          d="M 290 10 L 304 10 L 327.5 54 L 351 10 L 365 10 L 365 80 L 351 80 L 351 32 L 327.5 80 L 304 32 L 304 80 L 290 80 Z"
          fill="currentColor"
        />
      </g>
    </svg>
  );
}
