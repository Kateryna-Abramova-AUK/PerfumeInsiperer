"""Save Lost Cherry and verify the same visitor sees it after an API pod is replaced."""
import argparse
from contextlib import contextmanager
from http.cookiejar import CookieJar
import json
import subprocess
from tempfile import TemporaryFile
import time
from urllib.error import HTTPError, URLError
from urllib.request import HTTPCookieProcessor, ProxyHandler, Request, build_opener


DEPLOYMENT = "deployment/scent-mood-backend"
FRAGRANCE_ID = "lost-cherry"


@contextmanager
def port_forward(kubectl, port, opener):
    base = f"http://127.0.0.1:{port}/api"
    with TemporaryFile() as log, subprocess.Popen(
        [*kubectl, "port-forward", "service/scent-mood-frontend", f"{port}:80", "--address", "127.0.0.1"],
        stdout=log, stderr=log,
    ) as process:
        try:
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("kubectl port-forward stopped before the API was reachable")
                try:
                    with opener.open(f"{base}/health", timeout=3) as response:
                        response.read()
                        break
                except (URLError, TimeoutError):
                    time.sleep(0.5)
            else:
                raise RuntimeError("Timed out waiting for the forwarded API")
            yield base
        except Exception:
            log.seek(max(0, log.tell() - 1400))
            diagnostics = log.read().decode(errors="replace").strip()
            if diagnostics:
                print(diagnostics, flush=True)
            raise
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()


def backend_pod(kubectl):
    raw = subprocess.check_output([*kubectl, "get", "pods", "-l", "app=scent-mood-backend", "-o", "json"], text=True)
    pods = [
        item["metadata"] for item in json.loads(raw)["items"]
        if not item["metadata"].get("deletionTimestamp")
        and any(condition["type"] == "Ready" and condition["status"] == "True"
                for condition in item["status"].get("conditions", []))
    ]
    if len(pods) != 1:
        raise RuntimeError(f"Expected one ready API pod, found {len(pods)}")
    return pods[0]


def saved_names(opener, base):
    for attempt in range(3):
        try:
            with opener.open(f"{base}/shortlist", timeout=10) as response:
                return {item["id"]: item["name"] for item in json.load(response)}
        except (URLError, TimeoutError) as failure:
            if attempt == 2 or isinstance(failure, HTTPError) and failure.code < 500:
                raise
            time.sleep(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--context", required=True)
    parser.add_argument("--namespace", default="scent-mood")
    parser.add_argument("--local-port", type=int, default=18890)
    args = parser.parse_args()

    kubectl = ["kubectl", "--context", args.context, "--namespace", args.namespace]
    opener = build_opener(ProxyHandler({}), HTTPCookieProcessor(CookieJar()))
    before = backend_pod(kubectl)
    with port_forward(kubectl, args.local_port, opener) as base:
        if saved_names(opener, base):
            raise RuntimeError("Expected a new visitor with an empty shortlist")
        with opener.open(Request(f"{base}/shortlist/{FRAGRANCE_ID}", method="PUT"), timeout=15) as response:
            if response.status != 204:
                raise RuntimeError(f"Unexpected save status: {response.status}")
        saved = saved_names(opener, base).get(FRAGRANCE_ID)
        if not saved:
            raise RuntimeError("The saved fragrance was missing before pod replacement")
        print(f"Saved {saved} through {before['name']}", flush=True)

        subprocess.run([*kubectl, "rollout", "restart", DEPLOYMENT], check=True)
        subprocess.run([*kubectl, "rollout", "status", DEPLOYMENT, "--timeout=180s"], check=True)
        after = backend_pod(kubectl)
        if before["uid"] == after["uid"]:
            raise RuntimeError("The API pod did not change")
        if saved_names(opener, base).get(FRAGRANCE_ID) != saved:
            raise RuntimeError("The saved fragrance disappeared after pod replacement")
    print(f"{saved} is still saved through replacement pod {after['name']}")


if __name__ == "__main__":
    main()
