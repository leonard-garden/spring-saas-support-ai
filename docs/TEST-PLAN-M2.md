# Test Plan — M2: Knowledge Base & RAG
**Version:** 1.0  
**Date:** 2026-05-26  
**Tester:** _______________  
**Environment:** _______________  
**Build/Commit:** _______________

---

## Mục lục
1. [Môi trường & Chuẩn bị](#1-môi-trường--chuẩn-bị)
2. [Test Data](#2-test-data)
3. [P1 — Document Upload & Management](#3-p1--document-upload--management)
4. [P2 — Async Ingestion Pipeline](#4-p2--async-ingestion-pipeline)
5. [P3 — Hybrid RAG Search](#5-p3--hybrid-rag-search)
6. [P4 — Retry Failed Document](#6-p4--retry-failed-document)
7. [Security & Tenant Isolation](#7-security--tenant-isolation)
8. [Infrastructure](#8-infrastructure)
9. [Regression Checklist (M1 features)](#9-regression-checklist-m1-features)
10. [Sign-off Criteria](#10-sign-off-criteria)

---

## Legend
| Symbol | Nghĩa |
|--------|-------|
| ✅ PASS | Test case passed |
| ❌ FAIL | Test case failed — ghi bug ID vào cột Notes |
| ⏭ SKIP | Bỏ qua có lý do |
| 🔲 TODO | Chưa thực hiện |

---

## 1. Môi trường & Chuẩn bị

### 1.1 Dev (local)
```bash
# Khởi động toàn bộ stack
docker-compose up -d

# Verify services
docker ps                          # postgres, minio, minio-init, mailhog, app
curl http://localhost:8081/actuator/health   # → {"status":"UP"}
curl http://localhost:9000/minio/health/live # → 200
```

**MinIO console:** http://localhost:9001 (admin/minioadmin)  
**Mailhog:** http://localhost:8025  
**API base:** http://localhost:8081/api/v1  
**Swagger:** http://localhost:8081/swagger-ui.html

### 1.2 Staging (Render)
**API base:** https://_______________  
**OPENAI_API_KEY:** set ✅ / ❌  
**MINIO_ENDPOINT (R2):** set ✅ / ❌  
**DATABASE_URL:** set ✅ / ❌  

### 1.3 Tài khoản test
| Role | Email | Password | Tenant |
|------|-------|----------|--------|
| Owner A | test-owner-a@example.com | Test1234! | Tenant A |
| Owner B | test-owner-b@example.com | Test1234! | Tenant B |
| Member A | test-member-a@example.com | Test1234! | Tenant A |

> Tạo tài khoản trước khi bắt đầu test. Dùng POST /auth/register hoặc UI signup.

---

## 2. Test Data

### 2.1 File cần chuẩn bị
| ID | File | Loại | Kích thước | Nội dung đặc biệt |
|----|------|-------|------------|-------------------|
| F01 | `sample.pdf` | application/pdf | ~500KB | Text thông thường, tiếng Anh |
| F02 | `sample.txt` | text/plain | ~50KB | Plain text, nhiều đoạn văn |
| F03 | `sample.md` | text/markdown | ~20KB | Markdown với headers, code blocks |
| F04 | `large.pdf` | application/pdf | **11MB** | Vượt giới hạn 10MB |
| F05 | `image.png` | image/png | ~200KB | Ảnh, không phải text |
| F06 | `encrypted.pdf` | application/pdf | ~300KB | PDF có password (dùng để test FAILED) |
| F07 | `empty.txt` | text/plain | 0 bytes | File rỗng |
| F08 | `unicode.txt` | text/plain | ~10KB | Nội dung tiếng Việt/Unicode |

> **Tạo F06 (encrypted PDF):** Mở bất kỳ PDF → "Save as" → đặt password. Hoặc dùng: `qpdf --encrypt password123 password123 128 -- input.pdf encrypted.pdf`

### 2.2 Keyword dùng để search
Ghi lại các keyword nằm trong F01, F02, F03 sau khi upload thành công để dùng ở Section 5.

| File | Keyword đặc trưng | Ghi chú |
|------|------------------|---------|
| F01 | _______________ | |
| F02 | _______________ | |
| F03 | _______________ | |

---

## 3. P1 — Document Upload & Management

### TC-P1-01: Upload PDF thành công
**Precondition:** Đăng nhập Owner A, chưa có document nào.  

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Mở KbPage | Hiển thị empty state với CTA "Upload your first document" | 🔲 | |
| 2 | Click Upload | Modal mở ra với file picker và drag-drop zone | 🔲 | |
| 3 | Chọn file F01 (sample.pdf) | File được chọn, tên file hiện trong modal | 🔲 | |
| 4 | Click Submit/Upload | Modal đóng | 🔲 | |
| 5 | Quan sát table | Document xuất hiện với status badge **PENDING** | 🔲 | |
| 6 | Ghi lại document ID | ID: _______________ | 🔲 | |
| 7 | API: `GET /kb/documents/{id}` | `{"status":"PENDING", "chunkCount":null, "errorMessage":null}` | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P1-02: Upload TXT và MD
**Precondition:** Đăng nhập Owner A.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Upload F02 (sample.txt) | 202 ACCEPTED, status=PENDING | 🔲 | |
| 2 | Upload F03 (sample.md) | 202 ACCEPTED, status=PENDING | 🔲 | |
| 3 | Kiểm tra table | Cả 2 files xuất hiện trong danh sách | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P1-03: Upload file vượt 10MB
**Precondition:** Đăng nhập Owner A.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Chọn file F04 (11MB PDF) trong modal | | 🔲 | |
| 2 | Click Submit | Error message: **"File exceeds maximum size of 10MB"** | 🔲 | |
| 3 | Modal vẫn mở (không đóng) | User có thể chọn file khác | 🔲 | |
| 4 | API trực tiếp: `POST /kb/documents` với F04 | `400 Bad Request`, errorCode=`FILE_VALIDATION` | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P1-04: Upload file không được hỗ trợ
**Precondition:** Đăng nhập Owner A.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Upload F05 (image.png) qua API | `400 Bad Request`, message chứa "not supported" | 🔲 | |
| 2 | Upload F07 (empty.txt) qua API | `400 Bad Request`, message chứa "File is required" | 🔲 | |
| 3 | Kiểm tra MinIO console | Không có object nào được tạo cho 2 file trên | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P1-05: Upload không có authentication
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `POST /kb/documents` không có JWT header | `401 Unauthorized` | 🔲 | |
| 2 | `GET /kb/documents` không có JWT | `401 Unauthorized` | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P1-06: Xem danh sách documents
**Precondition:** Owner A đã upload ít nhất 2 documents.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `GET /kb/documents` | Trả về list tất cả documents của tenant A | 🔲 | |
| 2 | `GET /kb/documents?status=PENDING` | Chỉ documents có status=PENDING | 🔲 | |
| 3 | `GET /kb/documents?status=READY` | Chỉ documents READY | 🔲 | |
| 4 | `GET /kb/documents?status=FAILED` | Chỉ documents FAILED | 🔲 | |
| 5 | Kiểm tra thứ tự | Documents sắp xếp theo createdAt DESC (mới nhất trước) | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P1-07: Xem chi tiết document
**Precondition:** Có document đã READY.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `GET /kb/documents/{readyDocId}` | Trả về đầy đủ fields: id, filename, contentType, status=READY, sizeBytes, chunkCount>0, errorMessage=null | 🔲 | |
| 2 | `GET /kb/documents/{nonExistentId}` | `404 Not Found`, errorCode=`DOCUMENT_NOT_FOUND` | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P1-08: Xóa document — happy path
**Precondition:** Có document với status READY hoặc PENDING.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `DELETE /kb/documents/{readyDocId}` | `204 No Content` | 🔲 | |
| 2 | `GET /kb/documents/{readyDocId}` | `404 Not Found` | 🔲 | |
| 3 | Kiểm tra MinIO console | Object đã bị xóa khỏi bucket | 🔲 | |
| 4 | Kiểm tra DB | Không còn record trong bảng `documents` | 🔲 | |
| 5 | Kiểm tra DB | Không còn rows trong `document_chunks` cho docId này | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P1-09: Xóa document đang PROCESSING
**Precondition:** Upload file lớn và thao tác ngay khi status=PROCESSING.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Upload F01, ngay lập tức call DELETE khi thấy status=PROCESSING | `409 Conflict`, errorCode=`DOCUMENT_IN_PROGRESS` | 🔲 | |
| 2 | MinIO object vẫn tồn tại | Object không bị xóa | 🔲 | |

> **Tip:** Có thể pause OpenAI mock hoặc dùng môi trường với latency cao để giữ PROCESSING lâu hơn.

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P1-10: Upload file sau khi đã xóa tất cả (re-create KB)
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Xóa tất cả documents hiện có | 204 mỗi lần | 🔲 | |
| 2 | Upload F01 mới | 202 ACCEPTED — KB được tạo lại nếu chưa tồn tại | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

## 4. P2 — Async Ingestion Pipeline

### TC-P2-01: Pipeline PENDING → PROCESSING → READY
**Precondition:** Owner A, upload F01 (sample.pdf), OPENAI_API_KEY hợp lệ.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Ngay sau upload | status=**PENDING**, chunkCount=null | 🔲 | |
| 2 | Poll GET /kb/documents/{id} mỗi 2s | Sau vài giây: status=**PROCESSING** | 🔲 | |
| 3 | Tiếp tục poll | Cuối cùng: status=**READY** | 🔲 | |
| 4 | Kiểm tra chunkCount | chunkCount > 0 | 🔲 | |
| 5 | Kiểm tra errorMessage | errorMessage = null | 🔲 | |
| 6 | DB: `SELECT COUNT(*) FROM document_chunks WHERE document_id = '{id}'` | = chunkCount | 🔲 | |
| 7 | Thời gian tổng | PENDING → READY ≤ 60s cho file ~500KB | 🔲 | Ghi: ___s |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P2-02: Ingestion thất bại — PDF có password
**Precondition:** Owner A, upload F06 (encrypted.pdf).

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Upload F06 | 202 ACCEPTED, status=PENDING | 🔲 | |
| 2 | Poll đến khi final state | status=**FAILED** | 🔲 | |
| 3 | Kiểm tra errorMessage | Không null, chứa mô tả lỗi | 🔲 | |
| 4 | Kiểm tra chunkCount | chunkCount = 0 | 🔲 | |
| 5 | DB: document_chunks cho docId này | 0 rows | 🔲 | |
| 6 | MinIO | Object vẫn tồn tại (file không bị xóa khi FAILED) | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P2-03: FE status polling — badge update tự động
**Precondition:** Đang mở KbPage, vừa upload F01.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Mở KbPage, KHÔNG refresh trang | Badge hiển thị PENDING (màu vàng/xám) | 🔲 | |
| 2 | Chờ 10-30s | Badge tự động chuyển sang PROCESSING | 🔲 | |
| 3 | Chờ thêm | Badge tự động chuyển sang READY (màu xanh) | 🔲 | |
| 4 | chunkCount hiển thị | Số chunks xuất hiện trong row | 🔲 | |
| 5 | Không có lỗi JS console | Mở DevTools → Console → không có errors | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P2-04: Upload F08 (Unicode/tiếng Việt)
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Upload F08 | 202 ACCEPTED | 🔲 | |
| 2 | Poll đến final state | READY hoặc FAILED với error rõ ràng | 🔲 | |
| 3 | Nếu READY: search bằng keyword tiếng Việt trong F08 | Trả về kết quả chứa nội dung từ F08 | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P2-05: Upload 3 files đồng thời (concurrent)
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Gửi đồng thời 3 requests upload (F01, F02, F03) | Cả 3 nhận 202 ACCEPTED | 🔲 | |
| 2 | Poll cả 3 docs | Tất cả đạt READY hoặc FAILED — không có doc nào bị treo PROCESSING | 🔲 | |
| 3 | Kiểm tra DB | Chunks của doc A không lẫn vào doc B/C | 🔲 | |
| 4 | MinIO | 3 objects riêng biệt, đúng paths | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

## 5. P3 — Hybrid RAG Search

> **Precondition cho tất cả search tests:** Ít nhất F01 + F02 đã READY. Ghi lại keywords từ Section 2.2 trước khi test.

### TC-P3-01: Search cơ bản — keyword có trong documents
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `GET /kb/search?q=<keyword từ F01>` | 200, results không rỗng | 🔲 | |
| 2 | Kiểm tra response structure | Mỗi result có: chunkContent (không rỗng), score (0-1), documentName | 🔲 | |
| 3 | Kiểm tra ordering | Results sắp xếp theo score DESC | 🔲 | |
| 4 | Top result | documentName khớp với file chứa keyword | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P3-02: Search — keyword không tồn tại trong KB
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `GET /kb/search?q=xyzabc123nonsense` | 200, results = [] (mảng rỗng) | 🔲 | |
| 2 | FE search panel | Hiển thị empty state "No results found" — không phải error page | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P3-03: Search khi chưa có KB / chưa có docs READY
**Precondition:** Đăng nhập Owner B (chưa upload gì).

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `GET /kb/search?q=anything` | 200, results = [] | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P3-04: Search validation
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `GET /kb/search?q=` (blank) | 400 Bad Request | 🔲 | |
| 2 | `GET /kb/search` (thiếu param q) | 400 Bad Request | 🔲 | |
| 3 | `GET /kb/search?q=a` (1 ký tự) | 200 — tùy config, không crash | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P3-05: Search injection safety
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `GET /kb/search?q='; DROP TABLE documents; --` | 200, không crash, không xóa data | 🔲 | |
| 2 | `GET /kb/search?q=<script>alert(1)</script>` | 200, response là JSON — không execute script | 🔲 | |
| 3 | Sau 2 tests trên: `GET /kb/documents` | Vẫn trả về đủ documents — không mất data | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P3-06: Hybrid search — so sánh vector vs FTS
> Test này xác nhận rằng cả 2 kênh (vector + FTS) đang hoạt động.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Search keyword chính xác từ F01 (exact match) | Top results phải đến từ F01 | 🔲 | |
| 2 | Search bằng câu hỏi ngữ nghĩa liên quan đến F02 (không dùng từ chính xác trong file) | Vẫn trả về kết quả từ F02 (vector semantic search hoạt động) | 🔲 | |
| 3 | Kiểm tra score range | Tất cả scores trong khoảng [0, 1] | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P3-07: FE Search Panel — UX flow
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Mở search panel | Input field focus, placeholder text hiển thị | 🔲 | |
| 2 | Nhập keyword → Enter hoặc click Search | Loading indicator xuất hiện | 🔲 | |
| 3 | Results load xong | List hiển thị: documentName, excerpt, score badge | 🔲 | |
| 4 | Nhập keyword khác | Results cũ biến mất, results mới xuất hiện (không stale) | 🔲 | |
| 5 | Xóa hết input (clear) | Results cleared hoặc placeholder quay lại | 🔲 | |
| 6 | Search khi offline (disable network) | Error state — không treo UI | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

## 6. P4 — Retry Failed Document

> **Precondition:** Có ít nhất 1 document với status=**FAILED** (xem TC-P2-02).

### TC-P4-01: Retry FAILED document — happy path
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Ghi lại docId có status=FAILED | DocId: _______________ | 🔲 | |
| 2 | `POST /kb/documents/{failedDocId}/retry` | `200 OK`, body: `{status: "PENDING", errorMessage: null, chunkCount: 0}` | 🔲 | |
| 3 | Ngay sau retry | status=PENDING | 🔲 | |
| 4 | DB: `SELECT * FROM document_chunks WHERE document_id='{failedDocId}'` | 0 rows (chunks đã bị xóa trước khi re-process) | 🔲 | |
| 5 | Poll đến final state | READY hoặc FAILED (nếu file vẫn encrypted) | 🔲 | |
| 6 | Nếu dùng file hợp lệ thay thế: sau retry → READY | chunkCount > 0 | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P4-02: Retry document không phải FAILED
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `POST /kb/documents/{pendingDocId}/retry` | `409 Conflict`, errorCode=`DOCUMENT_NOT_RETRYABLE`, message="Only FAILED documents can be retried" | 🔲 | |
| 2 | `POST /kb/documents/{processingDocId}/retry` | `409 Conflict`, errorCode=`DOCUMENT_NOT_RETRYABLE` | 🔲 | |
| 3 | `POST /kb/documents/{readyDocId}/retry` | `409 Conflict`, errorCode=`DOCUMENT_NOT_RETRYABLE` | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P4-03: Retry document không tồn tại
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `POST /kb/documents/00000000-0000-0000-0000-000000000000/retry` | `404 Not Found`, errorCode=`DOCUMENT_NOT_FOUND` | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P4-04: Retry liên tiếp — double retry
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Retry FAILED doc lần 1 | 200, status=PENDING | 🔲 | |
| 2 | Ngay lập tức retry lần 2 (doc đang PENDING) | `409 Conflict`, errorCode=`DOCUMENT_NOT_RETRYABLE` | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-P4-05: Chunks cũ bị cleanup trước khi re-process
**Precondition:** Document đã FAILED sau một lần processing thành công rồi fail lại (partial failure), hoặc có orphaned chunks.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Insert thủ công 2 chunk rows cho failedDocId vào DB | Chunks tồn tại | 🔲 | |
| 2 | `POST /kb/documents/{failedDocId}/retry` | 200 OK | 🔲 | |
| 3 | Ngay sau retry (trước khi processing hoàn tất): `SELECT COUNT(*) FROM document_chunks WHERE document_id='{failedDocId}'` | 0 rows — chunks cũ đã bị xóa | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

## 7. Security & Tenant Isolation

### TC-SEC-01: Cross-tenant document access
**Precondition:** Owner A có doc X. Owner B đã login.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Dùng JWT của Owner B: `GET /kb/documents/{docXId}` | `404 Not Found` (không phải 403 — không leak thông tin tồn tại) | 🔲 | |
| 2 | Dùng JWT của Owner B: `DELETE /kb/documents/{docXId}` | `404 Not Found` | 🔲 | |
| 3 | Dùng JWT của Owner B: `POST /kb/documents/{docXId}/retry` | `404 Not Found` | 🔲 | |
| 4 | Dùng JWT của Owner B: `GET /kb/documents` | Trả về documents của Owner B (rỗng nếu chưa upload) — không thấy doc của Owner A | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-SEC-02: Cross-tenant search isolation
**Precondition:** Owner A có READY documents. Owner B chưa có gì.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Dùng JWT của Owner B: `GET /kb/search?q=<keyword từ doc của Owner A>` | 200, results = [] — không thấy data của Owner A | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-SEC-03: JWT expired / invalid
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Request với JWT hết hạn | `401 Unauthorized` | 🔲 | |
| 2 | Request với JWT sai chữ ký | `401 Unauthorized` | 🔲 | |
| 3 | Request với JWT của user khác tenant | Chỉ thấy data của tenant trong JWT | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-SEC-04: Error response không leak nội bộ
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Trigger FAILED document | errorMessage không chứa stack trace hay file path nội bộ | 🔲 | |
| 2 | 404 response | Không chứa SQL, class name, line number | 🔲 | |
| 3 | 409 response cho retry | Message: "Only FAILED documents can be retried" — không có UUID, không có current status trong message | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

## 8. Infrastructure

### TC-INFRA-01: docker-compose up — fully automated
**Precondition:** Không có container nào đang chạy.

```bash
docker-compose down -v   # clean slate
docker-compose up -d
```

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `docker ps` sau 30s | postgres, minio, mailhog, app — tất cả **healthy** hoặc **running** | 🔲 | |
| 2 | `docker logs <minio-init-container>` | Exit 0, không có error | 🔲 | |
| 3 | MinIO console: http://localhost:9001 | Bucket `support-ai-kb` đã tồn tại | 🔲 | |
| 4 | `curl http://localhost:8081/actuator/health` | `{"status":"UP"}` | 🔲 | |
| 5 | Flyway migrations | Log app: "Successfully applied N migrations" | 🔲 | |
| 6 | Upload F01 ngay sau `docker-compose up` | 202 ACCEPTED — không cần bước thủ công nào | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-INFRA-02: .env.example — đầy đủ biến
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Copy `.env.example` → `.env`, điền giá trị thật | App start thành công | 🔲 | |
| 2 | Kiểm tra từng group: Database, JWT, OpenAI, Storage, Mail, App URL | Tất cả documented với mô tả | 🔲 | |
| 3 | Comment R2 trong `.env.example` | Hướng dẫn R2 endpoint format có đầy đủ | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

### TC-INFRA-03: Cloudflare R2 compatibility (nếu có credentials)
**Precondition:** Có R2 bucket và credentials.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Set: `MINIO_ENDPOINT=https://<account>.r2.cloudflarestorage.com` | | 🔲 | |
| 2 | Upload F01 | 202 ACCEPTED | 🔲 | |
| 3 | Kiểm tra R2 console | Object xuất hiện trong bucket với path `{tenantId}/{docId}/sample.pdf` | 🔲 | |
| 4 | Poll đến READY | Ingestion hoàn thành | 🔲 | |
| 5 | DELETE document | 204, object bị xóa khỏi R2 | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________ / ⏭ SKIP (lý do: _______________)

---

### TC-INFRA-04: pgvector extension trên Render PostgreSQL
| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | Connect Render DB: `\dx` | Extension `vector` có trong list | 🔲 | |
| 2 | Flyway history: `SELECT * FROM flyway_schema_history ORDER BY installed_rank` | V13 applied thành công | 🔲 | |
| 3 | Schema: `\d document_chunks` | Column `embedding vector(1536)` và `tsv tsvector` tồn tại | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________ / ⏭ SKIP (lý do: _______________)

---

### TC-INFRA-05: Staging smoke test (Render full E2E)
**Precondition:** App deployed trên Render với đủ env vars.

| # | Bước | Expected | Result | Notes |
|---|------|----------|--------|-------|
| 1 | `GET /actuator/health` | `{"status":"UP"}` | 🔲 | |
| 2 | `POST /auth/register` → lấy JWT | 201, nhận accessToken | 🔲 | |
| 3 | `POST /kb/documents` (F01) với JWT | 202 ACCEPTED | 🔲 | |
| 4 | Poll cho đến READY | status=READY trong ≤ 120s (network latency staging) | 🔲 | Thời gian: ___s |
| 5 | `GET /kb/search?q=<keyword>` | 200, results không rỗng | 🔲 | |
| 6 | `POST /kb/documents/{id}/retry` (upload encrypted PDF trước) | 200 OK | 🔲 | |
| 7 | `DELETE /kb/documents/{id}` | 204, xác nhận trong R2/MinIO | 🔲 | |

**Result:** ✅ / ❌  **Notes:** _______________

---

## 9. Regression Checklist (M1 features)

> Đảm bảo M2 không break các tính năng của M1.

| # | Feature M1 | Test nhanh | Result | Notes |
|---|-----------|-----------|--------|-------|
| R01 | Register tài khoản mới | POST /auth/register → 201 | 🔲 | |
| R02 | Login | POST /auth/login → 200 + tokens | 🔲 | |
| R03 | Refresh token | POST /auth/refresh → 200 + new accessToken | 🔲 | |
| R04 | Logout | POST /auth/logout → 204 | 🔲 | |
| R05 | Forgot password email | POST /auth/forgot-password → email nhận được | 🔲 | |
| R06 | Reset password | POST /auth/reset-password → 200 | 🔲 | |
| R07 | Email verification flow | Click link verify → account verified | 🔲 | |
| R08 | Invite member | POST /members/invite → email sent | 🔲 | |
| R09 | Accept invitation | PUT /invitations/accept → member added | 🔲 | |
| R10 | List members | GET /members → 200, list đúng | 🔲 | |
| R11 | Remove member | DELETE /members/{id} → 204 | 🔲 | |
| R12 | Change member role | PATCH /members/{id}/role → 200 | 🔲 | |
| R13 | Dashboard stats | GET /dashboard → 200 | 🔲 | |
| R14 | Tenant isolation members | Member B không thấy member của Tenant A | 🔲 | |

**Regression Pass Rate:** _____ / 14  
**Result:** ✅ / ❌  **Blockers:** _______________

---

## 10. Sign-off Criteria

### Mandatory (phải PASS hết trước khi release)
- [ ] TC-P1-01 đến TC-P1-07 pass (core upload/management)
- [ ] TC-P2-01 (PENDING → READY pipeline) pass
- [ ] TC-P2-02 (FAILED state) pass
- [ ] TC-P2-03 (FE polling) pass
- [ ] TC-P3-01 (search cơ bản) pass
- [ ] TC-P3-05 (injection safety) pass
- [ ] TC-P4-01 (retry happy path) pass
- [ ] TC-P4-02 (retry guard) pass
- [ ] TC-SEC-01 (cross-tenant isolation) pass
- [ ] TC-SEC-02 (cross-tenant search) pass
- [ ] TC-INFRA-01 (docker-compose self-contained) pass
- [ ] TC-INFRA-05 (staging E2E) pass
- [ ] Regression R01–R14: ≥ 12/14 pass (không có R01, R02 fail)

### Optional (có thể defer sang hotfix)
- [ ] TC-INFRA-03 (R2 live test) — nếu không có R2 credentials
- [ ] TC-P2-05 (concurrent upload)
- [ ] TC-P4-05 (manual chunk injection test)

---

## Bug Log

| Bug ID | TC | Mức độ | Mô tả | Ngày | Status |
|--------|-----|--------|-------|------|--------|
| | | | | | |
| | | | | | |
| | | | | | |

**Severity levels:** 🔴 Critical | 🟠 High | 🟡 Medium | 🟢 Low

---

## Sign-off

| | Tester | Date | Signature |
|--|--------|------|-----------|
| **QA Completed** | | | |
| **Dev Review** | | | |
| **Release Approved** | | | |
