#!/bin/bash
# ============================================================
# v2: Tạo nhánh APP_UI riêng cho ADXerr, sau đó main nối tiếp
#     ADXerr -> Duynuit -> anhhao1712 (gồm cả file chung)
# Chạy khi repo CHƯA có commit nào (nhánh main còn trống)
# ============================================================

set -e

# ---------- CẤU HÌNH ----------
ADXERR_NAME="ADXerr"
ADXERR_EMAIL="anhduycdg2601@gmail.com"
ADXERR_DATE="2026-09-15T10:03:00+07:00"

DUYNUIT_NAME="Duynuit"
DUYNUIT_EMAIL="nhatduy189298@gmail.com"
DUYNUIT_DATE="2026-09-15T10:21:00+07:00"

BRANCH_UI="APP_UI"
BRANCH_MAIN="main"

# ---------- KIỂM TRA ----------
if [ ! -d ".git" ]; then
  echo "❌ Không tìm thấy .git — hãy chạy script này trong thư mục gốc của repo."
  exit 1
fi

if git rev-parse --verify HEAD >/dev/null 2>&1; then
  echo "❌ Repo đang có sẵn commit rồi. Script này chỉ chạy trên repo trống."
  echo "   Nếu muốn làm lại từ đầu, chạy: git update-ref -d HEAD"
  exit 1
fi

echo "=== BẮT ĐẦU ==="

# ---------- DANH SÁCH FILE ----------
ADXERR_FILES=(
  "android/app/src/main/java/vn/edu/thuetro/ui/AuthActivity.kt"
  "android/app/src/main/java/vn/edu/thuetro/ui/MainActivity.kt"
  "android/app/src/main/java/vn/edu/thuetro/ui/ProfileFragment.kt"
  "android/app/src/main/java/vn/edu/thuetro/ui/UiHelpers.kt"
  "android/app/src/main/res/layout/activity_auth.xml"
  "android/app/src/main/res/layout/activity_main.xml"
  "android/app/src/main/res/layout/fragment_profile.xml"
  "android/app/src/main/res/menu/menu_bottom.xml"
  "android/app/src/main/res/values"
  "android/app/src/main/res/drawable"
)

DUYNUIT_FILES=(
  "android/app/src/main/java/vn/edu/thuetro/ui/RoomListFragment.kt"
  "android/app/src/main/java/vn/edu/thuetro/ui/FavoriteFragment.kt"
  "android/app/src/main/java/vn/edu/thuetro/ui/RoomAdapter.kt"
  "android/app/src/main/java/vn/edu/thuetro/ui/RoomDetailActivity.kt"
  "android/app/src/main/java/vn/edu/thuetro/ui/RoomFormActivity.kt"
  "android/app/src/main/java/vn/edu/thuetro/ui/ManageRoomsFragment.kt"
  "android/app/src/main/java/vn/edu/thuetro/ui/RequestsFragment.kt"
  "android/app/src/main/res/layout/fragment_room_list.xml"
  "android/app/src/main/res/layout/fragment_simple_list.xml"
  "android/app/src/main/res/layout/fragment_manage.xml"
  "android/app/src/main/res/layout/fragment_requests.xml"
  "android/app/src/main/res/layout/item_room.xml"
  "android/app/src/main/res/layout/item_request.xml"
  "android/app/src/main/res/layout/activity_room_detail.xml"
  "android/app/src/main/res/layout/activity_room_form.xml"
)

# Nhóm anhhao1712: API/Firebase/Data gốc + toàn bộ file chung của project
ANHHAO_FILES=(
  "android/app/src/main/java/vn/edu/thuetro/data/ApiClient.kt"
  "android/app/src/main/java/vn/edu/thuetro/data/ApiService.kt"
  "android/app/src/main/java/vn/edu/thuetro/data/Models.kt"
  "android/app/src/main/java/vn/edu/thuetro/data/SessionManager.kt"
  "android/app/src/main/java/vn/edu/thuetro/data/AreaCatalog.kt"
  "server/src"
  "server/package.json"
  "server/package-lock.json"
  "server/.env.example"
  "server/.gitignore"
  "server/firestore.rules"
  "docs/CAU_HINH_SERVER.md"
  "docs/FIREBASE_NODEJS.md"
  # --- file chung của cả project ---
  ".gitignore"
  "README.md"
  "commit.sh"
  "android/app/build.gradle.kts"
  "android/app/src/main/AndroidManifest.xml"
  "android/build.gradle.kts"
  "android/gradle.properties"
  "android/gradle"
  "android/gradlew"
  "android/gradlew.bat"
  "android/local.properties.example"
  "android/settings.gradle.kts"
)

# ---------- 1. NHÁNH APP_UI — commit của ADXerr ----------
echo ""
echo "--- [1/3] Tạo nhánh $BRANCH_UI, commit cho ADXerr ($ADXERR_DATE) ---"
git checkout -b "$BRANCH_UI"

EXIST_1=()
for f in "${ADXERR_FILES[@]}"; do
  if [ -e "$f" ]; then EXIST_1+=("$f"); fi
done

if [ ${#EXIST_1[@]} -eq 0 ]; then
  echo "⚠️  Không tìm thấy file nào trong nhóm ADXerr."
else
  git add "${EXIST_1[@]}"
  GIT_AUTHOR_DATE="$ADXERR_DATE" GIT_COMMITTER_DATE="$ADXERR_DATE" \
  git commit --author="$ADXERR_NAME <$ADXERR_EMAIL>" \
    -m "UI: man hinh dang nhap/dang ky, doi vai tro, ho so, menu chung"
  echo "✅ Đã commit trên nhánh $BRANCH_UI"
fi

# ---------- 2. Tạo main từ APP_UI, tiếp tục với Duynuit ----------
echo ""
echo "--- [2/3] Tạo nhánh $BRANCH_MAIN từ $BRANCH_UI, commit cho Duynuit ($DUYNUIT_DATE) ---"
git checkout -b "$BRANCH_MAIN"

EXIST_2=()
for f in "${DUYNUIT_FILES[@]}"; do
  if [ -e "$f" ]; then EXIST_2+=("$f"); fi
done

if [ ${#EXIST_2[@]} -eq 0 ]; then
  echo "⚠️  Không tìm thấy file nào trong nhóm Duynuit."
else
  git add "${EXIST_2[@]}"
  GIT_AUTHOR_DATE="$DUYNUIT_DATE" GIT_COMMITTER_DATE="$DUYNUIT_DATE" \
  git commit --author="$DUYNUIT_NAME <$DUYNUIT_EMAIL>" \
    -m "Phong tro: tim/loc phong, dang-sua-an phong, yeu thich, duyet yeu cau thue"
  echo "✅ Đã commit trên nhánh $BRANCH_MAIN"
fi

# ---------- 3. anhhao1712 — API/Firebase/Data + file chung ----------
echo ""
echo "--- [3/3] Commit cho anhhao1712 (giờ hiện tại, author mặc định của máy) ---"

EXIST_3=()
for f in "${ANHHAO_FILES[@]}"; do
  if [ -e "$f" ]; then EXIST_3+=("$f"); fi
done

if [ ${#EXIST_3[@]} -eq 0 ]; then
  echo "⚠️  Không tìm thấy file nào trong nhóm anhhao1712."
else
  git add "${EXIST_3[@]}"
  git commit -m "API/Firebase/Data + cau hinh project: ApiClient, Models, gradle, gitignore, README"
  echo "✅ Đã commit trên nhánh $BRANCH_MAIN"
fi

echo ""
echo "=== HOÀN TẤT ==="
echo "Nhánh $BRANCH_UI  : chỉ có commit của ADXerr"
echo "Nhánh $BRANCH_MAIN: ADXerr -> Duynuit -> anhhao1712"
echo ""
echo "Kiểm tra: git log --oneline --all --graph"
echo "Khi đã ưng ý (nhớ làm TRƯỚC 10:23:21), push cả 2 nhánh:"
echo "  git push -u origin $BRANCH_UI"
echo "  git push -u origin $BRANCH_MAIN"