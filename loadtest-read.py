#!/usr/bin/env python3
"""Read-only mr.x load test.

Uses only GET requests. It is intentionally fail-closed: no write endpoint is
accepted. Run against a staging Supabase/API endpoint first.
"""
import argparse, concurrent.futures, time, urllib.request, urllib.error

ALLOWED_PATHS = ("/rest/v1/posts", "/rest/v1/stories", "/rest/v1/profiles", "/rest/v1/notifications", "/rest/v1/conversations", "/rest/v1/messages")

def one(url, headers, timeout):
    started = time.perf_counter()
    try:
        req = urllib.request.Request(url, method="GET", headers=headers)
        with urllib.request.urlopen(req, timeout=timeout) as r:
            r.read(4096)
            code = r.status
    except urllib.error.HTTPError as e:
        code = e.code
    except Exception:
        code = 0
    return time.perf_counter() - started, code

def run_stage(url, headers, timeout, requests, concurrency):
    started = time.perf_counter()
    results = []
    # Submit only a bounded batch at a time so a large test does not create
    # thousands of queued Future objects and memory pressure on the generator.
    remaining = requests
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as ex:
        while remaining:
            batch = min(remaining, concurrency * 4)
            futures = [ex.submit(one, url, headers, timeout) for _ in range(batch)]
            for f in concurrent.futures.as_completed(futures):
                results.append(f.result())
            remaining -= batch
    elapsed = time.perf_counter() - started
    lat = sorted(x[0] for x in results)
    ok = sum(200 <= x[1] < 300 for x in results)
    errors = len(results) - ok
    def pct(q): return lat[min(len(lat)-1, max(0, int(len(lat)*q)-1))] * 1000
    return {
        "requests": len(results), "concurrency": concurrency, "elapsed": elapsed,
        "throughput": len(results)/elapsed if elapsed else 0,
        "success": ok, "errors": errors,
        "p50": pct(.50), "p95": pct(.95), "p99": pct(.99), "max": lat[-1]*1000,
        "codes": {c: sum(1 for _, code in results if code == c) for c in sorted(set(code for _, code in results))},
    }

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--url", required=True, help="A read-only REST URL, e.g. https://host/rest/v1/posts?...")
    p.add_argument("--requests", type=int, default=1000)
    p.add_argument("--concurrency", type=int, default=50)
    p.add_argument("--timeout", type=float, default=10)
    p.add_argument("--apikey", default="")
    p.add_argument("--local", action="store_true", help="Allow only loopback HTTP for the local load-test harness")
    p.add_argument("--ramp", default="", help="Comma-separated concurrency stages, e.g. 25,50,100,250")
    args = p.parse_args()
    is_local = args.url.startswith(("http://127.0.0.1:", "http://localhost:"))
    if (not args.url.startswith("https://") and not (args.local and is_local)) or not any(args.url.split("?",1)[0].endswith(x) for x in ALLOWED_PATHS):
        raise SystemExit("Refusing URL: HTTPS read-only mr.x REST endpoint required (or --local loopback harness)")
    if args.requests < 1 or args.concurrency < 1:
        raise SystemExit("requests and concurrency must be positive")
    stages = [args.concurrency]
    if args.ramp:
        try:
            stages = [int(x) for x in args.ramp.split(",") if x.strip()]
        except ValueError:
            raise SystemExit("--ramp must contain positive integers separated by commas")
        if not stages or any(x < 1 for x in stages):
            raise SystemExit("--ramp values must be positive")
    headers = {"apikey": args.apikey} if args.apikey else {}
    print("stage,requests,concurrency,throughput_req_s,success,errors,p50_ms,p95_ms,p99_ms,max_ms")
    for concurrency in stages:
        r = run_stage(args.url, headers, args.timeout, args.requests, concurrency)
        print(f"stage,{r['requests']},{r['concurrency']},{r['throughput']:.2f},{r['success']},{r['errors']},{r['p50']:.1f},{r['p95']:.1f},{r['p99']:.1f},{r['max']:.1f}")
        if r["errors"]:
            print("codes:", r["codes"])


if __name__ == "__main__":
    main()
