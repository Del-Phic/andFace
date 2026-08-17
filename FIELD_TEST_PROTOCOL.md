# 현장 성능 테스트 절차

## 목표
- 정확도 80% 이상.
- 미등록자 false accept 0건.
- 등록자는 마스크나 안대를 착용해도 관측 가능 특징으로 인증.

## 시나리오
- USER_1 clean, mask, left patch, right patch, glasses.
- USER_2 clean, mask, left patch, right patch, glasses.
- USER_3 clean, mask, left patch, right patch, glasses.
- Unregistered person clean and occluded.

## 기록
- Record result, best user, scores, coverage, margin, liveness, observable count, and feature_count=164.
- Save every trial in FIELD_TEST_RESULTS_TEMPLATE.csv.
- 앱에서 결과 패널을 길게 눌러 Fuzzy, Mahalanobis, final score, coverage, margin, liveness, observable count, identity consistency 값을 CSV 행으로 복사할 수 있습니다.
