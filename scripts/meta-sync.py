#!/usr/bin/env python3
"""meta/ → dictcli 동기화 스크립트

meta 노트의 한글 타이틀 ↔ 영어 태그 매핑을 dictcli edn으로 출력.
기존 graph.edn과 비교하여 delta만 출력한다.

사용법:
  # dry-run (stdout에 edn 출력)
  python3 scripts/meta-sync.py

  # dictcli에 직접 import
  python3 scripts/meta-sync.py > data/meta-harvest-sync.edn
  dictcli import data/meta-harvest-sync.edn

전략:
- 한글 단어가 항상 동일한 영어 태그와만 등장하면 "완벽 매핑" (자동)
- 여러 태그와 등장하면 최다빈도 태그를 50% 이상일 때만 (반자동)
- :source 트리플로 한글→denote ID 연결
"""

import os, re, sys
from collections import defaultdict
from datetime import datetime

META_DIR = os.path.expanduser("~/sync/org/meta")
GRAPH_EDN = os.path.expanduser("~/repos/gh/dictcli/graph.edn")
SKIP_TAGS = {'meta', 'metameta', 'bib', 'botlog', 'llmlog', 'syntopicon', 'propaedia'}

# 알려진 오매핑 (수동 제외)
EXCLUDE = {
    ('확증편향', 'truth'),
    ('도전', 'prototyping'),
    ('덤벨', 'kettlebell'),  # 둘 다 맞지만 덤벨≠케틀벨
    ('모바일', 'android'),   # 모바일 ⊃ 안드로이드
}

def is_korean(w):
    return any('\uac00' <= c <= '\ud7a3' for c in w)

def load_existing(path):
    existing_trans = set()
    existing_source = set()
    if not os.path.exists(path):
        return existing_trans, existing_source
    with open(path) as f:
        for line in f:
            m = re.match(r'\s*\["([^"]+)"\s+:trans\s+"([^"]+)"\]', line)
            if m:
                existing_trans.add((m.group(1), m.group(2)))
            m = re.match(r'\s*\["([^"]+)"\s+:source\s+"([^"]+)"\]', line)
            if m:
                existing_source.add((m.group(1), m.group(2)))
    return existing_trans, existing_source

def harvest_meta():
    ko_to_eng = defaultdict(lambda: defaultdict(int))
    ko_to_id = defaultdict(set)

    for fname in sorted(os.listdir(META_DIR)):
        if not fname.endswith('.org'):
            continue
        m = re.match(r'(\d{8}T\d{6})[^-]*--(.+)__(.+)\.org$', fname)
        if not m:
            continue
        ident = m.group(1)
        title = re.sub(r'[†‡#¤§©@⊨◊]', '', m.group(2))
        words = [w.strip() for w in title.split('-') if w.strip()]
        korean = [w for w in words if is_korean(w)]
        tags = [t for t in m.group(3).split('_')
                if t and t not in SKIP_TAGS and t.isascii()]

        for kw in korean:
            ko_to_id[kw].add(ident)
            for tag in tags:
                ko_to_eng[kw][tag] += 1

    return ko_to_eng, ko_to_id

def main():
    existing_trans, existing_source = load_existing(GRAPH_EDN)
    ko_to_eng, ko_to_id = harvest_meta()

    new_trans = []
    new_source = []

    for kw in sorted(ko_to_eng.keys()):
        tags_freq = sorted(ko_to_eng[kw].items(), key=lambda x: -x[1])
        top_tag, top_count = tags_freq[0]
        total = sum(v for v in ko_to_eng[kw].values())

        # 완벽 매핑 (100%) 또는 지배적 (>=50%)
        if top_count == total or (top_count / total >= 0.5):
            if (kw, top_tag) not in existing_trans and (kw, top_tag) not in EXCLUDE:
                new_trans.append((kw, top_tag))

    for kw in sorted(ko_to_id.keys()):
        for ident in sorted(ko_to_id[kw]):
            if (kw, ident) not in existing_source:
                new_source.append((kw, ident))

    # 통계 (stderr)
    print(f"[meta-sync] meta 한글: {len(ko_to_eng)}, "
          f"기존 trans: {len(existing_trans)}, "
          f"신규 trans: {len(new_trans)}, "
          f"신규 source: {len(new_source)}", file=sys.stderr)

    # edn 출력 (stdout)
    now = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
    print(f';; meta-harvest-sync.edn — meta→dictcli 자동 동기화')
    print(f';; 생성: {now}')
    print(f';; 신규 :trans {len(new_trans)}개, :source {len(new_source)}개')
    print()
    print('[')
    if new_trans:
        print(' ;; === :trans (한글 → 영어 태그) ===')
        for kw, tag in new_trans:
            print(f' ["{kw}" :trans "{tag}"]')
    if new_source:
        print()
        print(' ;; === :source (한글 → denote ID) ===')
        for kw, ident in new_source:
            print(f' ["{kw}" :source "{ident}"]')
    print(']')

if __name__ == '__main__':
    main()
