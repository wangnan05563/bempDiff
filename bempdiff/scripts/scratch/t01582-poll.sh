#!/bin/bash
# 轮询 GitHub Actions run 37727572654（linux arm64）直到收口。
# 教训：api.github.com 常整段重置返回空体——单次空响应不能当失败，需重试并继续。
cd "D:/code/otherProjects/18_comparePakage"
RUN=37727572654
API="https://api.github.com/repos/wangnan05563/bempDiff/actions/runs/$RUN"
for i in $(seq 1 130); do
  TOK=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | grep '^password=' | cut -d= -f2)
  J=""
  for t in 1 2 3; do
    J=$(curl -sS -m 40 -H "Authorization: Bearer $TOK" -H "Accept: application/vnd.github+json" "$API" 2>/dev/null)
    [ -n "$J" ] && break
    sleep 20
  done
  if [ -z "$J" ]; then echo "[$i] network reset x3 ($(date +%H:%M:%S))"; sleep 60; continue; fi
  ST=$(echo "$J" | python -c "import json,sys
try:
    d=json.load(sys.stdin); print(d.get('status'), d.get('conclusion'))
except Exception as e: print('parse-err', e)" 2>/dev/null)
  echo "[$i] $ST ($(date +%H:%M:%S))"
  case "$ST" in
    completed*|failed*)
      echo "FINAL: $ST"
      curl -sS -m 40 -H "Authorization: Bearer $TOK" "$API/jobs" | python -c "
import json,sys
d=json.load(sys.stdin)
for j in d.get('jobs',[]):
    print(j['name'],'|',j['status'],j.get('conclusion'),'| steps:',len(j.get('steps',[])))
    for s in j.get('steps',[]):
        if s.get('conclusion') not in (None,'success','skipped'):
            print('   BADSTEP', s['name'], s.get('conclusion'))
" 
      curl -sS -m 40 -H "Authorization: Bearer $TOK" "https://api.github.com/repos/wangnan05563/bempDiff/actions/runs/$RUN/artifacts" | python -c "
import json,sys
d=json.load(sys.stdin)
for a in d.get('artifacts',[]): print('ARTIFACT', a['name'], a.get('size_in_bytes'), 'expired=',a.get('expired'))
"
      exit 0;;
  esac
  sleep 75
done
echo "GIVEUP after 130 polls"
