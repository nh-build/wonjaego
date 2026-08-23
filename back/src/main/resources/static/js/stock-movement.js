// Shared behavior for movements/stock-in.html and movements/stock-out.html.
// STOCK_IN vs STOCK_OUT screens differ only in: sign shown on the stepper value, whether
// quantities are clamped to current stock, and which reason chips are wired up (each
// template renders its own chip markup — this script wires whatever .reason-chip
// elements it finds).
function initStockMovementForm(options) {
    'use strict';

    var stockIn = options.stockIn === true;

    var state = {
        product: null,
        quantities: {}
    };

    var searchInput = document.getElementById('search-input');
    var searchResults = document.getElementById('search-results');
    var scanButton = document.getElementById('scan-button');
    var scannerModal = document.getElementById('scanner-modal');
    var scannerClose = document.getElementById('scanner-close');
    var barcodeInput = document.getElementById('barcode-input');
    var barcodeLookupButton = document.getElementById('barcode-lookup-button');
    var barcodeError = document.getElementById('barcode-error');
    var selectedSection = document.getElementById('selected-product-section');
    var selectedName = document.getElementById('selected-product-name');
    var selectedBarcode = document.getElementById('selected-product-barcode');
    var changeButton = document.getElementById('change-product-button');
    var reasonChips = document.getElementById('reason-chips');
    var chipButtons = reasonChips ? Array.prototype.slice.call(reasonChips.querySelectorAll('.reason-chip')) : [];
    var variantSection = document.getElementById('variant-section');
    var variantRows = document.getElementById('variant-rows');
    var productIdInput = document.getElementById('product-id-input');
    var typeInput = document.getElementById('type-input');
    var submitButton = document.getElementById('submit-button');
    var html5QrCode = null;
    var searchTimer = null;

    function formatDelta(magnitude) {
        return (stockIn ? '+' : '-') + magnitude;
    }

    function updateSubmitState() {
        var hasProduct = !!state.product;
        var hasType = !!typeInput.value;
        var hasQuantity = Object.keys(state.quantities).some(function (id) {
            return state.quantities[id] > 0;
        });
        submitButton.disabled = !(hasProduct && hasType && hasQuantity);
    }

    function clearVariantRows() {
        variantRows.innerHTML = '';
        state.quantities = {};
        variantSection.classList.add('hidden');
    }

    function renderVariantRows(variants) {
        variantRows.innerHTML = '';
        state.quantities = {};
        variants.forEach(function (variant, index) {
            state.quantities[variant.id] = 0;

            var row = document.createElement('div');
            row.className = 'variant-row';

            var label = document.createElement('div');
            label.className = 'variant-label';
            label.textContent = variant.optionLabel || state.product.productName;

            var current = document.createElement('div');
            current.className = 'variant-current';
            current.textContent = '현재 ' + variant.stockQuantity;

            var stepper = document.createElement('div');
            stepper.className = 'stepper';

            var minus = document.createElement('button');
            minus.type = 'button';
            minus.className = 'stepper-button';
            minus.textContent = '−';
            minus.setAttribute('aria-label', '수량 감소');

            var value = document.createElement('span');
            value.className = 'stepper-value';
            value.textContent = formatDelta(0);

            var plus = document.createElement('button');
            plus.type = 'button';
            plus.className = 'stepper-button';
            plus.textContent = '+';
            plus.setAttribute('aria-label', '수량 증가');

            var variantIdInput = document.createElement('input');
            variantIdInput.type = 'hidden';
            variantIdInput.name = 'entries[' + index + '].variantId';
            variantIdInput.value = variant.id;

            var quantityInput = document.createElement('input');
            quantityInput.type = 'hidden';
            quantityInput.name = 'entries[' + index + '].quantity';
            quantityInput.value = '0';

            function applyDelta(direction) {
                var next = (state.quantities[variant.id] || 0) + direction;
                if (next < 0) {
                    next = 0;
                }
                if (!stockIn && next > variant.stockQuantity) {
                    next = variant.stockQuantity;
                }
                state.quantities[variant.id] = next;
                value.textContent = formatDelta(next);
                quantityInput.value = String(next);
                plus.disabled = !stockIn && next >= variant.stockQuantity;
                updateSubmitState();
            }

            minus.addEventListener('click', function () { applyDelta(-1); });
            plus.addEventListener('click', function () { applyDelta(1); });
            plus.disabled = !stockIn && variant.stockQuantity <= 0;

            stepper.appendChild(minus);
            stepper.appendChild(value);
            stepper.appendChild(plus);

            row.appendChild(label);
            row.appendChild(current);
            row.appendChild(stepper);
            row.appendChild(variantIdInput);
            row.appendChild(quantityInput);
            variantRows.appendChild(row);
        });
        variantSection.classList.toggle('hidden', variants.length === 0);
    }

    function selectProduct(data) {
        state.product = data;
        selectedSection.classList.remove('hidden');
        selectedName.textContent = data.productName;
        if (data.matchedSku) {
            selectedBarcode.textContent = '바코드 ' + data.matchedSku;
            selectedBarcode.classList.remove('hidden');
        } else {
            selectedBarcode.textContent = '';
            selectedBarcode.classList.add('hidden');
        }
        productIdInput.value = data.productId;
        renderVariantRows(data.variants || []);
        updateSubmitState();
    }

    function deselectProduct() {
        state.product = null;
        selectedSection.classList.add('hidden');
        productIdInput.value = '';
        clearVariantRows();
        updateSubmitState();
    }

    function renderSearchResults(results) {
        searchResults.innerHTML = '';
        if (!results || !results.length) {
            searchResults.classList.add('hidden');
            return;
        }
        searchResults.classList.remove('hidden');
        results.forEach(function (result) {
            var item = document.createElement('button');
            item.type = 'button';
            item.className = 'search-result-item';
            item.textContent = result.name;
            item.addEventListener('click', function () {
                fetch('/products/' + result.id + '/stock-entry')
                    .then(function (res) { return res.json(); })
                    .then(function (data) {
                        selectProduct(data);
                        searchInput.value = '';
                        renderSearchResults([]);
                    });
            });
            searchResults.appendChild(item);
        });
    }

    searchInput.addEventListener('input', function () {
        var query = searchInput.value.trim();
        clearTimeout(searchTimer);
        if (!query) {
            renderSearchResults([]);
            return;
        }
        searchTimer = setTimeout(function () {
            fetch('/products/search?q=' + encodeURIComponent(query))
                .then(function (res) { return res.json(); })
                .then(renderSearchResults)
                .catch(function () { renderSearchResults([]); });
        }, 250);
    });

    searchInput.addEventListener('keydown', function (event) {
        if (event.key === 'Enter') {
            event.preventDefault();
        }
    });

    changeButton.addEventListener('click', deselectProduct);

    chipButtons.forEach(function (chip) {
        chip.addEventListener('click', function () {
            chipButtons.forEach(function (c) { c.classList.remove('selected'); });
            chip.classList.add('selected');
            typeInput.value = chip.getAttribute('data-type');
            updateSubmitState();
        });
    });

    function lookupBarcode() {
        var sku = barcodeInput.value.trim();
        if (!sku) {
            barcodeError.textContent = '바코드를 입력해주세요.';
            return;
        }
        barcodeError.textContent = '조회 중...';
        fetch('/products/by-sku?sku=' + encodeURIComponent(sku))
            .then(function (res) {
                if (!res.ok) {
                    throw new Error('not-found');
                }
                return res.json();
            })
            .then(function (data) {
                barcodeError.textContent = '';
                selectProduct(data);
                closeScanner();
            })
            .catch(function () {
                barcodeError.textContent = '바코드로 상품을 찾을 수 없어요.';
            });
    }

    function openScanner() {
        barcodeInput.value = '';
        barcodeError.textContent = '';
        scannerModal.classList.remove('hidden');
        if (typeof Html5Qrcode === 'undefined') {
            barcodeError.textContent = '카메라 라이브러리를 불러오지 못했어요. 위 입력창에 바코드를 직접 입력해주세요.';
            return;
        }
        html5QrCode = new Html5Qrcode('qr-reader');
        html5QrCode
            .start(
                { facingMode: 'environment' },
                { fps: 10, qrbox: { width: 250, height: 150 } },
                function onScanSuccess(decodedText) {
                    barcodeInput.value = decodedText;
                    lookupBarcode();
                },
                function onScanFailure() {
                    // Per-frame no-read — expected constantly while aiming, not an error.
                }
            )
            .catch(function () {
                barcodeError.textContent = '카메라를 열 수 없어요. HTTPS(또는 localhost) 환경인지 확인하거나, 위 입력창에 바코드를 직접 입력해주세요.';
            });
    }

    function closeScanner() {
        scannerModal.classList.add('hidden');
        if (html5QrCode) {
            var scanner = html5QrCode;
            html5QrCode = null;
            scanner.stop().then(function () { scanner.clear(); }).catch(function () { /* already stopped */ });
        }
    }

    scanButton.addEventListener('click', openScanner);
    scannerClose.addEventListener('click', closeScanner);
    barcodeLookupButton.addEventListener('click', lookupBarcode);
    barcodeInput.addEventListener('keydown', function (event) {
        if (event.key === 'Enter') {
            event.preventDefault();
            lookupBarcode();
        }
    });

    if (typeof INITIAL_TYPE !== 'undefined' && INITIAL_TYPE) {
        var initialChip = chipButtons.filter(function (chip) { return chip.getAttribute('data-type') === INITIAL_TYPE; })[0];
        if (initialChip) {
            initialChip.click();
        }
    } else if (chipButtons.length) {
        chipButtons[0].click();
    }

    if (typeof INITIAL_PRODUCT_ID !== 'undefined' && INITIAL_PRODUCT_ID) {
        fetch('/products/' + INITIAL_PRODUCT_ID + '/stock-entry')
            .then(function (res) { return res.json(); })
            .then(selectProduct)
            .catch(function () { /* ownership already enforced server-side */ });
    }
}
