# Bundle Analyzer (L-6 follow-up)

The Next.js bundle analyzer is **not yet wired** because `@next/bundle-analyzer`
couldn't be installed in the dev sandbox. To enable locally:

```bash
npm install --save-dev @next/bundle-analyzer@^14
```

Then in `next.config.ts` add:

```ts
import bundleAnalyzer from "@next/bundle-analyzer";

const withBundleAnalyzer = bundleAnalyzer({
  enabled: process.env.ANALYZE === "true",
});

// at the bottom:
export default withBundleAnalyzer(withNextIntl(nextConfig));
```

And add a `build:analyze` script in `package.json`:

```json
"build:analyze": "ANALYZE=true next build"
```

Run:

```bash
npm run build:analyze
```

The analyzer opens `analyze/client.html` showing the bundle composition.
