#!/usr/bin/env python3
import argparse
import base64
import hashlib
import hmac
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from urllib.parse import urlparse


def read_env(path):
    values = {}
    with open(path, encoding="utf-8") as env_file:
        for raw_line in env_file:
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip("\"").strip("'")
    return values


def encode(value):
    raw = json.dumps(value, separators=(",", ":"), ensure_ascii=False).encode()
    return base64.urlsafe_b64encode(raw).rstrip(b"=")


def access_token(secret, user_id):
    algorithm = "HS512" if len(secret.encode()) >= 64 else "HS384" if len(secret.encode()) >= 48 else "HS256"
    digest = {
        "HS512": hashlib.sha512,
        "HS384": hashlib.sha384,
        "HS256": hashlib.sha256,
    }[algorithm]
    now = int(time.time())
    header = encode({"alg": algorithm, "typ": "JWT"})
    claims = encode({
        "jti": str(uuid.uuid4()),
        "sub": user_id,
        "type": "access",
        "actorType": "user",
        "roleCode": "user",
        "iat": now,
        "exp": now + 1800,
    })
    signing_input = header + b"." + claims
    signature = base64.urlsafe_b64encode(
        hmac.new(secret.encode(), signing_input, digest).digest()
    ).rstrip(b"=")
    return (signing_input + b"." + signature).decode()


def test_chat(base_url, token, provider):
    body = json.dumps({
        "provider": provider,
        "messages": [{"role": "user", "content": "请只回复：测试成功"}],
        "profileSummary": "健康功能测试",
    }, ensure_ascii=False).encode()
    request = urllib.request.Request(
        f"{base_url}/api/v1/ai/chat/stream",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        },
        method="POST",
    )
    token_chars = 0
    done = False
    error_event = None
    try:
        with urllib.request.urlopen(request, timeout=190) as response:
            status = response.status
            event_name = ""
            for raw_line in response:
                line = raw_line.decode("utf-8").strip()
                if line.startswith("event:"):
                    event_name = line[6:].strip()
                elif line.startswith("data:"):
                    data = line[5:].strip()
                    if event_name == "done" and data == "[DONE]":
                        done = True
                    elif event_name == "error":
                        error_event = data
                    else:
                        try:
                            token_chars += len(json.loads(data).get("token", ""))
                        except (json.JSONDecodeError, AttributeError):
                            pass
                    event_name = ""
    except urllib.error.HTTPError as error:
        print(f"chat provider={provider} http={error.code} passed=false")
        return False
    passed = status == 200 and token_chars > 0 and done and error_event is None
    print(
        f"chat provider={provider} http={status} tokenChars={token_chars} "
        f"done={str(done).lower()} passed={str(passed).lower()}"
    )
    return passed


def print_usage(base_url, token):
    request = urllib.request.Request(
        f"{base_url}/api/v1/ai/chat/daily-usage",
        headers={"Authorization": f"Bearer {token}"},
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        data = json.load(response).get("data", {})
    plan = data.get("plan", {})
    print(f"planUsage used={plan.get('used')} remaining={plan.get('remaining')}")


def list_plan_users(env):
    url = urlparse(env["DB_URL"].removeprefix("jdbc:"))
    query = """
        SELECT u.user_id, COALESCE(CAST(s.state_value AS SIGNED), 0)
        FROM user_account u
        JOIN ai_user_consent c ON c.user_id = u.user_id
          AND c.policy_version = '2026-07-17' AND c.revoked_at IS NULL
        LEFT JOIN app_ephemeral_state s
          ON s.state_key = CONCAT('hrp:ai:usage:', CURRENT_DATE, ':plan:', u.user_id)
          AND s.expires_at > CURRENT_TIMESTAMP(3)
        WHERE u.status = 1 AND u.deleted_at IS NULL
          AND COALESCE(CAST(s.state_value AS SIGNED), 0) < 3
        ORDER BY COALESCE(CAST(s.state_value AS SIGNED), 0), u.user_id
        LIMIT 10
    """
    process_env = os.environ.copy()
    process_env["MYSQL_PWD"] = env["DB_PASSWORD"]
    result = subprocess.run([
        "mysql", "-N", "-B", "-h", url.hostname, "-P", str(url.port or 3306),
        "-u", env["DB_USERNAME"], url.path.lstrip("/"), "-e", query,
    ], capture_output=True, text=True, env=process_env, check=True)
    print(result.stdout.strip())


def mysql_execute(env, query):
    url = urlparse(env["DB_URL"].removeprefix("jdbc:"))
    process_env = os.environ.copy()
    process_env["MYSQL_PWD"] = env["DB_PASSWORD"]
    result = subprocess.run([
        "mysql", "-N", "-B", "-h", url.hostname, "-P", str(url.port or 3306),
        "-u", env["DB_USERNAME"], url.path.lstrip("/"), "-e", query,
    ], capture_output=True, text=True, env=process_env, check=True)
    return result.stdout.strip()


def plan_usage_value(env, user_id):
    return mysql_execute(env, """
        SELECT state_value FROM app_ephemeral_state
        WHERE state_key = CONCAT('hrp:ai:usage:', CURRENT_DATE, ':plan:', '%s')
          AND expires_at > CURRENT_TIMESTAMP(3)
    """ % user_id)


def restore_plan_usage(env, user_id, value):
    key = "CONCAT('hrp:ai:usage:', CURRENT_DATE, ':plan:', '%s')" % user_id
    if value:
        mysql_execute(env, f"UPDATE app_ephemeral_state SET state_value = '{int(value)}' WHERE state_key = {key}")
    else:
        mysql_execute(env, f"DELETE FROM app_ephemeral_state WHERE state_key = {key}")


def test_plan(base_url, token, provider):
    body = json.dumps({
        "provider": provider,
        "age": 45,
        "gender": "male",
        "heightCm": 170,
        "weightKg": 70,
        "bmi": 24.2,
        "medicalHistory": "无",
        "recentBp": "120/80",
        "recentGlucose": 5.2,
        "recentTc": 4.5,
        "recentLdl": 2.6,
        "goal": "general",
        "dietPref": "light",
        "exerciseBase": "light",
    }, ensure_ascii=False).encode()
    request = urllib.request.Request(
        f"{base_url}/api/v1/ai/plan/generate",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=260) as response:
            status = response.status
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        print(f"plan provider={provider} http={error.code} passed=false")
        return False
    data = payload.get("data") or {}
    actual_provider = data.get("provider", "")
    try:
        plan = json.loads(data.get("rawJson", "{}"))
    except json.JSONDecodeError:
        plan = {}
    day_count = len(plan.get("days", [])) if isinstance(plan.get("days"), list) else 0
    passed = payload.get("code") == 0 and actual_provider == provider and day_count == 7
    print(
        f"plan requested={provider} actual={actual_provider} http={status} "
        f"days={day_count} passed={str(passed).lower()}"
    )
    return passed


def test_ark(env, provider):
    model_keys = {
        "doubao": ("AI_CHAT_DOUBAO_MODEL", "doubao-seed-2-1-pro-260628"),
        "glm": ("AI_CHAT_GLM_MODEL", "glm-5-2-260617"),
        "deepseek": ("AI_CHAT_DEEPSEEK_MODEL", "deepseek-v4-pro-260425"),
    }
    model_key, default_model = model_keys[provider]
    body = json.dumps({
        "model": env.get(model_key, default_model),
        "messages": [{"role": "user", "content": "请只回复：测试成功"}],
        "max_tokens": 20,
    }, ensure_ascii=False).encode()
    request = urllib.request.Request(
        "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
        data=body,
        headers={
            "Authorization": f"Bearer {env.get('AI_CHAT_VOLCENGINE_API_KEY', '')}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    status = 0
    try:
        with urllib.request.urlopen(request, timeout=130) as response:
            status = response.status
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        status = error.code
        payload = json.load(error)
    error = payload.get("error") or {}
    passed = status == 200 and bool(payload.get("choices"))
    message = str(error.get("message", "")).replace("\n", " ")[:160]
    print(
        f"ark provider={provider} http={status} errorCode={error.get('code', '')} "
        f"message={message!r} passed={str(passed).lower()}"
    )
    return passed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", required=True)
    parser.add_argument("--user")
    parser.add_argument("--direct-ark", action="store_true")
    parser.add_argument("--usage-only", action="store_true")
    parser.add_argument("--list-plan-users", action="store_true")
    parser.add_argument("--plan", action="store_true")
    parser.add_argument("--base-url", default="http://127.0.0.1:8081")
    parser.add_argument("providers", nargs="+", choices=["doubao", "glm", "deepseek"])
    args = parser.parse_args()
    env = read_env(args.env)
    if args.list_plan_users:
        list_plan_users(env)
        return
    if args.direct_ark:
        results = [test_ark(env, provider) for provider in args.providers]
        raise SystemExit(0 if all(results) else 1)
    if not args.user:
        raise SystemExit("--user is required for business endpoint tests")
    secret = env.get("JWT_SECRET", "")
    if len(secret.encode()) < 32:
        raise SystemExit("JWT_SECRET is missing or too short")
    token = access_token(secret, args.user)
    print_usage(args.base_url.rstrip("/"), token)
    if args.usage_only:
        return
    if args.plan:
        if not args.user.isdigit():
            raise SystemExit("--user must contain digits only")
        original_usage = plan_usage_value(env, args.user)
        results = []
        try:
            for provider in args.providers:
                restore_plan_usage(env, args.user, original_usage)
                results.append(test_plan(args.base_url.rstrip("/"), token, provider))
        finally:
            restore_plan_usage(env, args.user, original_usage)
        raise SystemExit(0 if all(results) else 1)
    results = [test_chat(args.base_url.rstrip("/"), token, provider) for provider in args.providers]
    raise SystemExit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
