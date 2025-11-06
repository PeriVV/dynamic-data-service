document.addEventListener('DOMContentLoaded', function() {
    // API and State
    const API_BASE = '/api/resolver-config';
    let currentStep = 1;
    let wizardData = {};
    const GRAPHQL_TYPES = ['String', 'Int', 'Float', 'Boolean', 'ID'];
    const ACTIVE_DS_KEY = 'activeDataSource'; // sessionStorage 存储键


    // DOM Element References
    const loadingOverlay = document.getElementById('loadingOverlay');
    const notification = document.getElementById('notification');
    const wizardContainer = document.getElementById('wizardMainContainer');
    const nextBtn = document.getElementById('nextBtn');
    const prevBtn = document.getElementById('prevBtn');
    const publishBtn = document.getElementById('publishBtn');
    const sqlQueryInput = document.getElementById('sqlQuery');
    const resolverNameInput = document.getElementById('resolverName');
    const operationTypeInput = document.getElementById('operationType');
    const descriptionInput = document.getElementById('description');
    const inputParamsContainer = document.getElementById('inputParametersContainer');
    const outputFieldsContainer = document.getElementById('outputFieldsContainer');
    const testParamsContainer = document.getElementById('testParametersContainer');
    const resolverList = document.getElementById('resolverList');

    // --- Initializer ---
    const initializeApp = () => {
        setupEventListeners();
        initDataSourceCards();
        resetWizard();
        loadResolvers();
        // 初始化时，把页面上默认选中的卡片写入 wizardData 与 sessionStorage，并探测徽章
        try {
          const selected = document.querySelector('.data-source-card.selected');
          if (selected) {
            const src = (selected.dataset.source || 'mysql').toUpperCase();
            wizardData.dataSource = src;
            sessionStorage.setItem(ACTIVE_DS_KEY, JSON.stringify({
              type: src,
              label: selected.querySelector('h5')?.innerText?.trim() || src,
              url: '' // 如需展示URL，后面会从 /info 补
            }));
            // 探测一下
            probeDataSourceAndPaintBadge(selected, src);
          }
        } catch {}

    };

    function initDataSourceCards() {
      const cards = document.querySelectorAll('.data-source-card');
      cards.forEach(card => {
        card.addEventListener('click', () => {
          // 1) 切换selected高亮
          cards.forEach(c => c.classList.remove('selected'));
          card.classList.add('selected');

          // 2) 写入当前数据源
          const source = (card.dataset.source || '').toUpperCase(); // 'MYSQL' | 'POSTGRESQL' | 'DM8'
          wizardData.dataSource = source;
          // 持久化到 sessionStorage，带上展示名
          sessionStorage.setItem(ACTIVE_DS_KEY, JSON.stringify({
            type: source,
            label: card.querySelector('h5')?.innerText?.trim() || source,
            url: ''
          }));

          // 3) 触发预览联动
          generatePreview();

          // 4) 实时探测连通性，更新徽章
          probeDataSourceAndPaintBadge(card, source);

          // 5) 如果当前正处在第2步，刷新“当前数据源”条
          if (currentStep === 2) {
            renderActiveDsBar(); // 下面会给出实现
          }
        });
      });
    }

    // --- Event Listeners Setup ---
    const setupEventListeners = () => {
        nextBtn.addEventListener('click', nextStep);
        prevBtn.addEventListener('click', prevStep);
        publishBtn.addEventListener('click', publishResolver);
        document.getElementById('refreshResolversBtn').addEventListener('click', loadResolvers);
        document.getElementById('testQueryBtn').addEventListener('click', testQuery);
        document.getElementById('addOutputFieldBtn').addEventListener('click', () => addDynamicInputRow(outputFieldsContainer, true));
        sqlQueryInput.addEventListener('input', () => debounce(updateInputParamsFromSql, 300)());

        document.querySelectorAll('.wizard-step').forEach(step => {
            step.addEventListener('click', (e) => {
                const stepNum = parseInt(e.currentTarget.dataset.step);
                if (stepNum < currentStep) updateWizardStep(stepNum);
            });
        });

        [outputFieldsContainer, inputParamsContainer, testParamsContainer].forEach(container => {
            container.addEventListener('click', event => {
                if (event.target.closest('.btn-remove')) {
                    const rowToRemove = event.target.closest('.dynamic-input-row');
                    const key = rowToRemove.dataset.key;
                    rowToRemove.remove();
                    // Also remove corresponding test parameter if an input parameter is removed
                    if(container === inputParamsContainer) {
                        const testRow = testParamsContainer.querySelector(`.dynamic-input-row[data-key="${key}"]`);
                        if (testRow) testRow.remove();
                        checkEmptyState([inputParamsContainer, testParamsContainer]);
                    }
                }
            });
        });
        
        resolverList.addEventListener('click', async (event) => {
            const button = event.target.closest('button[data-action]');
            if (!button) return;
            const { action, id, name, resolver, type, description } = button.dataset;
            if (action === 'test') await openApiTestModal(id, resolver, type, description);
            else if (action === 'edit') editResolver(id);
            else if (action === 'delete') deleteResolver(id, name);
        });
    };
    
    // --- Core Logic ---
    const updateInputParamsFromSql = () => {
        const sql = sqlQueryInput.value;
        const paramRegex = /#\{([a-zA-Z0-9_]+)\}/g;
        const currentParams = new Set();
        let match;
        while ((match = paramRegex.exec(sql)) !== null) {
            currentParams.add(match[1]);
        }

        const existingParamRows = new Map();
        inputParamsContainer.querySelectorAll('.dynamic-input-row').forEach(row => {
            existingParamRows.set(row.dataset.key, row);
        });

        // Remove params that are no longer in the SQL
        existingParamRows.forEach((row, key) => {
            if (!currentParams.has(key)) {
                row.remove();
                const testRow = testParamsContainer.querySelector(`.dynamic-input-row[data-key="${key}"]`);
                if (testRow) testRow.remove();
            }
        });

        // Add new params found in the SQL
        currentParams.forEach(param => {
            if (!existingParamRows.has(param)) {
                addDynamicInputRow(inputParamsContainer, true, param);
                addTestParamRow(param);
            }
        });
        
        checkEmptyState([inputParamsContainer, testParamsContainer]);
    };

    const nextStep = () => {
        if (validateCurrentStep()) {
            if (currentStep < 3) {
                updateWizardStep(currentStep + 1);
                if (currentStep === 3) generatePreview();
            }
        }
    };
    
    const prevStep = () => {
        if (currentStep > 1) updateWizardStep(currentStep - 1);
    };

    // --- Wizard and State Management ---
    const updateWizardStep = (step) => {
        currentStep = step;
        document.querySelectorAll('.wizard-step').forEach((el, index) => {
            el.classList.toggle('completed', index + 1 < step);
            el.classList.toggle('active', index + 1 === step);
        });
        document.querySelectorAll('.step-content').forEach((content, index) => {
            content.classList.toggle('active', index + 1 === step);
        });
        prevBtn.style.display = step > 1 ? 'block' : 'none';
        nextBtn.style.display = step < 3 ? 'block' : 'none';
        nextBtn.innerHTML = step === 2 ? '预览发布 <i class="fas fa-eye"></i>' : '下一步 <i class="fas fa-arrow-right"></i>';
        // 在 updateWizardStep 末尾追加：
        if (step === 2) {
          renderActiveDsBar();   // 显示并刷新
        } else {
          const bar = document.getElementById('activeDsBar');
          if (bar) bar.classList.add('hidden'); // 其它步骤隐藏
        }
    };

    const resetWizard = () => {
        wizardData = { dataSource: 'MYSQL', editMode: false, editId: null };
        [resolverNameInput, descriptionInput, sqlQueryInput].forEach(i => i.value = '');
        operationTypeInput.value = 'QUERY';
        [inputParamsContainer, outputFieldsContainer, testParamsContainer].forEach(c => c.innerHTML = '');
        checkEmptyState([inputParamsContainer, testParamsContainer, outputFieldsContainer]);
        document.getElementById('queryPreview').innerHTML = '';
        publishBtn.innerHTML = '<i class="fas fa-rocket"></i> 发布API';
        updateWizardStep(1);
    };
    
    const validateCurrentStep = () => {
        if (currentStep === 2) {
            const resolverName = resolverNameInput.value.trim();
            if (!resolverName.match(/^[a-zA-Z_][a-zA-Z0-9_]*$/)) {
                showNotification('接口名称格式不正确', 'warning');
                return false;
            }
            wizardData = {
                ...wizardData,
                resolverName,
                operationType: operationTypeInput.value,
                description: descriptionInput.value.trim(),
                sqlQuery: sqlQueryInput.value.trim(),
                inputParameters: JSON.stringify(readDynamicInputs(inputParamsContainer, true)),
                outputFields: JSON.stringify(readDynamicInputs(outputFieldsContainer, true)),
            };
        }
        return true;
    };

    // --- API Calls ---
    async function loadResolvers() { /* ... (implementation is the same as before) ... */ }
    async function publishResolver() { /* ... (implementation is the same as before, just reads from wizardData) ... */ }
    async function editResolver(id) { /* ... (needs to be updated for new UI) ... */ }
    async function deleteResolver(id, name) { /* ... (implementation is the same as before) ... */ }
    async function testQuery() { /* ... (needs to read from new test UI) ... */ }

    // --- Dynamic Input UI Helpers ---
    const addDynamicInputRow = (container, withTypeDropdown, key = '', value = '') => {
        const row = document.createElement('div');
        row.className = 'dynamic-input-row';
        row.dataset.key = key; // Store the key for easy lookup

        const keyInput = withTypeDropdown
            ? `<input type="text" class="form-control key-input" placeholder="字段名" value="${escapeHtml(key)}" ${container === inputParamsContainer ? 'disabled' : ''}>`
            : `<input type="text" class="form-control key-input" placeholder="参数" value="${escapeHtml(key)}">`;
        
        const valueInput = withTypeDropdown
            ? `<select class="form-control value-input">${GRAPHQL_TYPES.map(t => `<option value="${t}" ${t === value ? 'selected' : ''}>${t}</option>`).join('')}</select>`
            : `<input type="text" class="form-control value-input" placeholder="值" value="${escapeHtml(value)}">`;

        const removeBtn = (withTypeDropdown && container === inputParamsContainer) ? '' : `<button type="button" class="btn-remove"><i class="fas fa-trash-alt"></i></button>`;
        
        row.innerHTML = `${keyInput}${valueInput}${removeBtn}`;
        container.appendChild(row);
        checkEmptyState([container]);
    };

    const addTestParamRow = (key) => {
        const row = document.createElement('div');
        row.className = 'dynamic-input-row';
        row.dataset.key = key;
        row.innerHTML = `
            <input type="text" class="form-control key-input" value="${escapeHtml(key)}" disabled>
            <input type="text" class="form-control value-input" placeholder="输入测试值">
        `;
        testParamsContainer.appendChild(row);
    };
    
    const readDynamicInputs = (container, isKeyValue = false) => {
        const data = {};
        container.querySelectorAll('.dynamic-input-row').forEach(row => {
            const key = row.querySelector('.key-input').value.trim();
            const value = row.querySelector('.value-input').value.trim();
            if (key) data[key] = isKeyValue ? value : value;
        });
        return data;
    };
    
    const checkEmptyState = (containers) => {
        containers.forEach(container => {
            const hasRows = container.querySelector('.dynamic-input-row');
            const emptyState = container.querySelector('.empty-state-small');
            if (hasRows && emptyState) {
                emptyState.style.display = 'none';
            } else if (!hasRows && !emptyState) {
                 container.innerHTML = `<div class="empty-state-small">${container === outputFieldsContainer ? '点击下方按钮添加' : '在SQL中添加参数...'}</div>`;
            } else if (!hasRows && emptyState) {
                emptyState.style.display = 'block';
            }
        });
    };
    
    // --- Utility Functions ---
    const escapeHtml = (text) => text ? String(text).replace(/[&<>"']/g, m => ({'&': '&amp;','<': '&lt;','>': '&gt;','"': '&quot;',"'": '&#039;'})[m]) : '';
    let debounceTimer;
    const debounce = (func, delay) => {
        return function() {
            const context = this;
            const args = arguments;
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => func.apply(context, args), delay);
        };
    };

    // Placeholder for functions that need full implementation
    // (Copying from previous correct implementation)
    async function loadResolvers() {
        showLoading(true);
        try {
            const response = await fetch(API_BASE);
            if (!response.ok) throw new Error('Failed to fetch resolvers.');
            const resolvers = await response.json();
            resolverList.innerHTML = resolvers.length === 0
                ? `<div class="empty-state"><div class="empty-state-icon"><i class="fas fa-database"></i></div><h5>还没有创建任何API接口</h5><p>使用上面的向导创建您的第一个数据接口</p></div>`
                : resolvers.map(r => `
                    <div class="resolver-card">
                        <div class="resolver-header"><span class="resolver-name">${escapeHtml(r.resolverName)}</span><span class="resolver-type">${r.operationType}</span></div>
                        <p class="resolver-description">${escapeHtml(r.description) || 'No description.'}</p>
                        <div class="resolver-actions">
                            <button class="btn btn-sm btn-outline" data-action="test" data-id="${r.id}" data-resolver="${r.resolverName}" data-type="${r.operationType}" data-description="${escapeHtml(r.description || '无描述')}"><i class="fas fa-vial"></i> 试用</button>
                            <button class="btn btn-sm btn-outline" data-action="edit" data-id="${r.id}"><i class="fas fa-edit"></i> 编辑</button>
                            <button class="btn btn-sm btn-outline" data-action="delete" data-id="${r.id}" data-name="${r.resolverName}" style="color:var(--danger-color);border-color:var(--danger-color);"><i class="fas fa-trash"></i> 删除</button>
                        </div>
                    </div>`).join('');
        } catch (error) {
            showNotification(`Error: ${error.message}`, 'error');
        } finally {
            showLoading(false);
        }
    }
    
    async function publishResolver() {
        showLoading(true);
        const isEdit = wizardData.editMode;
        const url = isEdit ? `${API_BASE}/${wizardData.editId}` : API_BASE;
        const method = isEdit ? 'PUT' : 'POST';
        try {
            const response = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(wizardData) });
            const result = await response.json();
            if (!result.success) throw new Error(result.message || '操作失败');
            showNotification(`API接口 ${isEdit ? '更新' : '发布'} 成功！`, 'success');
            resetWizard();
            await loadResolvers();
        } catch (error) {
            showNotification(`${isEdit ? '更新' : '发布'}失败: ${error.message}`, 'error');
        } finally {
            showLoading(false);
        }
    }

    async function editResolver(id) {
        showLoading(true);
        try {
            const response = await fetch(`${API_BASE}/${id}`);
            if (!response.ok) throw new Error('Could not fetch resolver details.');
            const config = await response.json();
            resetWizard();
            wizardData = { ...config, editMode: true, editId: id };
            resolverNameInput.value = config.resolverName;
            operationTypeInput.value = config.operationType;
            descriptionInput.value = config.description;
            sqlQueryInput.value = config.sqlQuery;
            
            // Populate dynamic fields
            const inputParams = JSON.parse(config.inputParameters || '{}');
            Object.entries(inputParams).forEach(([key, value]) => {
                addDynamicInputRow(inputParamsContainer, true, key, value);
                addTestParamRow(key);
            });
            const outputFields = JSON.parse(config.outputFields || '{}');
            Object.entries(outputFields).forEach(([key, value]) => addDynamicInputRow(outputFieldsContainer, true, key, value));
            
            checkEmptyState([inputParamsContainer, outputFieldsContainer, testParamsContainer]);
            publishBtn.innerHTML = '<i class="fas fa-save"></i> 更新API';
            updateWizardStep(2);
            wizardContainer.scrollIntoView({ behavior: 'smooth' });
            showNotification(`编辑模式: ${config.resolverName}`, 'info');
            // 在拿到 config 后（你已有 resetWizard() 之后的赋值），追加：
            wizardData.dataSource = (config.dataSource || 'MYSQL').toUpperCase();

            // 让相应卡片选中（如果页面存在三张卡）
            document.querySelectorAll('.data-source-card').forEach(c => {
              const ds = (c.dataset.source || '').toUpperCase();
              c.classList.toggle('selected', ds === wizardData.dataSource);
            });
            // 写入 sessionStorage
            sessionStorage.setItem(ACTIVE_DS_KEY, JSON.stringify({
              type: wizardData.dataSource,
              label: document.querySelector(`.data-source-card[data-source="${(wizardData.dataSource || '').toLowerCase()}"] h5`)?.innerText?.trim()
                      || wizardData.dataSource,
              url: ''
            }));

            // 若此时已在第2步/或切到第2步后，会显示
            renderActiveDsBar();

        } catch (error) {
            showNotification(`Error: ${error.message}`, 'error');
        } finally {
            showLoading(false);
        }
    }

    async function deleteResolver(id, name) {
        if (!confirm(`您确定要删除接口 "${name}" 吗?`)) return;
        showLoading(true);
        try {
            const response = await fetch(`${API_BASE}/${id}`, { method: 'DELETE' });
            const result = await response.json();
            if (!result.success) throw new Error(result.message || '删除失败');
            showNotification(`接口 "${name}" 已成功删除`, 'success');
            await loadResolvers();
        } catch (error) {
            showNotification(`删除失败: ${error.message}`, 'error');
        } finally {
            showLoading(false);
        }
    }

    // API试用模态框相关功能
    async function openApiTestModal(id, resolverName, operationType, description) {
        try {
            // 获取完整的resolver配置
            const response = await fetch(`${API_BASE}/${id}`);
            if (!response.ok) throw new Error('无法获取接口详情');
            const config = await response.json();
            
            // 填充模态框信息
            document.getElementById('modalResolverName').textContent = resolverName;
            const operationBadge = document.getElementById('modalOperationType');
            operationBadge.textContent = operationType;
            operationBadge.className = `badge ${operationType.toLowerCase()}`;
            document.getElementById('modalDescription').textContent = description;
            
            // 生成示例GraphQL查询
            const exampleQuery = generateGraphQLQuery(config);
            document.getElementById('modalGraphqlQuery').value = exampleQuery;
            
            // 重置结果显示
            resetModalResult();
            
            // 保存配置信息供重置使用
            document.getElementById('apiTestModal').dataset.currentConfig = JSON.stringify(config);
            
            // 显示模态框
            const modal = new bootstrap.Modal(document.getElementById('apiTestModal'));
            modal.show();
            
        } catch (error) {
            showNotification(`打开试用界面失败: ${error.message}`, 'error');
        }
    }
    
    function generateGraphQLQuery(config) {
        const { resolverName, operationType, inputParameters } = config;
        const params = JSON.parse(inputParameters || '{}');
        
        let query = '';
        if (operationType === 'QUERY') {
            query = `query {\n  ${resolverName}`;
        } else {
            query = `mutation {\n  ${resolverName}`;
        }
        
        // 添加参数
        if (Object.keys(params).length > 0) {
            const paramStrings = Object.entries(params).map(([key, type]) => {
                const exampleValue = getExampleValue(type);
                return `${key}: ${exampleValue}`;
            });
            query += `(${paramStrings.join(', ')})`;
        }
        
        query += ` {\n    # 在这里添加您想要返回的字段\n    # 例如: id, name, email\n  }\n}`;
        
        return query;
    }
    
    function getExampleValue(type) {
        switch (type.toLowerCase()) {
            case 'string': return '"示例文本"';
            case 'int': case 'integer': return '1';
            case 'float': case 'double': return '1.0';
            case 'boolean': return 'true';
            case 'id': return '"1"';
            default: return '"示例值"';
        }
    }
    
    function resetModalResult() {
        const resultDiv = document.getElementById('modalQueryResult');
        resultDiv.className = 'query-result-display';
        resultDiv.innerHTML = `
            <div class="text-muted text-center py-4">
                <i class="fas fa-play-circle fa-2x mb-2"></i>
                <p>点击"执行查询"按钮开始测试</p>
            </div>
        `;
    }

    async function testQuery() {
        const sql = sqlQueryInput.value.trim();
        if (!sql) return showNotification('请输入SQL查询语句', 'warning');
        
        const parameters = {};
        testParamsContainer.querySelectorAll('.dynamic-input-row').forEach(row => {
            const key = row.querySelector('.key-input').value;
            let value = row.querySelector('.value-input').value;
            if (!isNaN(value) && value.trim() !== '') {
                parameters[key] = parseFloat(value);
            } else {
                parameters[key] = value;
            }
        });

        showLoading(true);
        try {
            const response = await fetch(`${API_BASE}/test-sql`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                  sql,
                  parameters,
                  dataSource: (wizardData.dataSource || 'MYSQL').toUpperCase(),
                  useSandbox: true
                })
            });
            const result = await response.json();
            if (!response.ok) throw new Error(result.message || '测试查询失败');
            
            const previewDiv = document.getElementById('queryPreview');
            if (result.data && result.data.length > 0) {
                const headers = Object.keys(result.data[0]);
                previewDiv.innerHTML = `
                    <div class="query-result-container">
                        <div class="result-header">
                            <span class="result-count">共 ${result.data.length} 条记录</span>
                            ${result.data.length > 5 ? '<span class="result-note">（仅显示前5条）</span>' : ''}
                        </div>
                        <div class="table-container">
                            <table class="table table-sm table-striped">
                                <thead>
                                    <tr>${headers.map(h => `<th>${escapeHtml(h)}</th>`).join('')}</tr>
                                </thead>
                                <tbody>
                                    ${result.data.slice(0, 5).map(row => 
                                        `<tr>${headers.map(h => `<td title="${escapeHtml(String(row[h] || ''))}">${escapeHtml(String(row[h] || '').length > 50 ? String(row[h] || '').substring(0, 50) + '...' : String(row[h] || ''))}</td>`).join('')}</tr>`
                                    ).join('')}
                                </tbody>
                            </table>
                        </div>
                    </div>`;
            } else {
                 previewDiv.innerHTML = '<div class="text-muted p-3 text-center"><i class="fas fa-info-circle"></i> 查询成功，但没有返回任何数据。</div>';
            }
            showNotification('查询测试成功', 'success');
        } catch (error) {
            showNotification(`查询测试失败: ${error.message}`, 'error');
        } finally {
            showLoading(false);
        }
    }
    
    function generatePreview() {
        const previewContent = document.getElementById('previewContent');
        const exampleQuery = document.getElementById('exampleQuery');
        
        // 确保wizardData有默认值
        if (!wizardData.dataSource) {
            wizardData.dataSource = 'mysql';
        }
        
        if (previewContent) {
            const preview = `
                <div class="preview-section">
                    <h6><i class="fas fa-database"></i> 数据源</h6>
                    <p>${(wizardData.dataSource || 'mysql').toUpperCase()}</p>
                </div>
                
                <div class="preview-section">
                    <h6><i class="fas fa-code"></i> SQL查询</h6>
                    <pre class="sql-preview">${escapeHtml(wizardData.sqlQuery || '')}</pre>
                </div>
                
                <div class="preview-section">
                    <h6><i class="fas fa-cog"></i> 接口配置</h6>
                    <p><strong>名称:</strong> ${wizardData.resolverName || '未设置'}</p>
                    <p><strong>类型:</strong> ${wizardData.operationType || 'QUERY'}</p>
                    <p><strong>描述:</strong> ${wizardData.description || '无'}</p>
                </div>
                
                <div class="preview-section">
                    <h6><i class="fas fa-exchange-alt"></i> 参数配置</h6>
                    <p><strong>输入参数:</strong></p>
                    <pre>${wizardData.inputParameters || '{}'}</pre>
                    <p><strong>输出字段:</strong></p>
                    <pre>${wizardData.outputFields || '{}'}</pre>
                </div>
            `;
            
            previewContent.innerHTML = preview;
        }
        
        // 生成示例查询
        if (exampleQuery && wizardData.resolverName && wizardData.resolverName.trim()) {
            let inputParams = '';
            try {
                const params = JSON.parse(wizardData.inputParameters || '{}');
                const paramEntries = Object.entries(params);
                if (paramEntries.length > 0) {
                    inputParams = '(' + paramEntries.map(([key, type]) => {
                        let exampleValue;
                        switch (type.toLowerCase()) {
                            case 'int':
                            case 'integer':
                                exampleValue = '10';
                                break;
                            case 'string':
                                exampleValue = '"example"';
                                break;
                            case 'boolean':
                                exampleValue = 'true';
                                break;
                            default:
                                exampleValue = '"value"';
                        }
                        return `${key}: ${exampleValue}`;
                    }).join(', ') + ')';
                }
            } catch (e) {
                // 忽略JSON解析错误
            }
            
            let outputFields = '';
            try {
                const fields = JSON.parse(wizardData.outputFields || '{}');
                outputFields = Object.keys(fields).join('\n    ');
            } catch (e) {
                outputFields = 'id\n    name';
            }
            
            const queryType = wizardData.operationType.toLowerCase();
            const example = `${queryType} {
  ${wizardData.resolverName}${inputParams} {
    ${outputFields}
  }
}`;
            
            exampleQuery.textContent = example;
        }
    }
     
    const showLoading = (show) => { loadingOverlay.style.display = show ? 'flex' : 'none'; };
    const showNotification = (message, type = 'success') => {
        const notificationContent = notification.querySelector('.notification-content');
        notification.style.borderLeftColor = `var(--${type}-color, var(--primary-color))`;
        notificationContent.textContent = message;
        notification.classList.add('show');
        setTimeout(() => notification.classList.remove('show'), 3500);
    };

    // 执行GraphQL查询
    async function executeGraphQLQuery() {
        const query = document.getElementById('modalGraphqlQuery').value.trim();
        if (!query) {
            showNotification('请输入GraphQL查询语句', 'error');
            return;
        }
        
        const resultDiv = document.getElementById('modalQueryResult');
        
        // 显示加载状态
        resultDiv.className = 'query-result-display loading';
        resultDiv.innerHTML = `
            <div class="text-center">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">执行中...</span>
                </div>
                <p class="mt-2">正在执行查询...</p>
            </div>
        `;
        
        try {
            const response = await fetch('/graphql', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ query })
            });
            
            const result = await response.json();
            
            if (result.errors && result.errors.length > 0) {
                // 显示错误
                resultDiv.className = 'query-result-display error';
                resultDiv.textContent = `错误:\n${result.errors.map(e => e.message).join('\n')}`;
            } else {
                // 显示成功结果
                resultDiv.className = 'query-result-display success';
                resultDiv.textContent = JSON.stringify(result.data, null, 2);
            }
            
        } catch (error) {
            resultDiv.className = 'query-result-display error';
            resultDiv.textContent = `网络错误: ${error.message}`;
        }
    }
    
    // 重置指令
    function resetGraphQLQuery() {
        const modal = bootstrap.Modal.getInstance(document.getElementById('apiTestModal'));
        if (modal && modal._element.dataset.currentConfig) {
            const config = JSON.parse(modal._element.dataset.currentConfig);
            document.getElementById('modalGraphqlQuery').value = generateGraphQLQuery(config);
        }
        resetModalResult();
    }

    async function probeDataSourceAndPaintBadge(cardEl, sourceUpper) {
      const key = sourceUpper.toLowerCase();
      const badge = document.querySelector(`[data-ds-badge="${key}"]`);
      if (!badge) return;

      badge.className = 'badge bg-warning text-dark';
      badge.textContent = '检测中…';

      try {
        const resp = await fetch(`/api/datasources/${encodeURIComponent(sourceUpper)}/status`, {
          headers: { 'Accept': 'application/json' }
        });

        if (!resp.ok) {
          badge.className = 'badge bg-danger';
          badge.textContent = '连接失败';
          return;
        }

        const data = await resp.json().catch(() => ({}));
        const configured = !!data.configured;
        const ok = !!data.ok;

        if (!configured) {
          badge.className = 'badge bg-secondary';
          badge.textContent = '未配置';
        } else if (ok) {
          badge.className = 'badge bg-success';
          badge.textContent = '已连接';
        } else {
          badge.className = 'badge bg-danger';
          // 有 message 就展示一点
          badge.textContent = data.message ? ('连接失败: ' + String(data.message).slice(0, 30)) : '连接失败';
        }
      } catch (e) {
        badge.className = 'badge bg-danger';
        badge.textContent = '连接异常';
      }
    }

    function renderActiveDsBar() {
      const bar   = document.getElementById('activeDsBar');
      const badge = document.getElementById('activeDsBadge');
      const label = document.getElementById('activeDsLabel');
      const urlEl = document.getElementById('activeDsUrl');

      if (!bar || !badge || !label || !urlEl) return;

      // 从 wizardData 优先取；兜底用 sessionStorage
      let type = (wizardData.dataSource || '').toUpperCase();
      let saved = null;
      try { saved = JSON.parse(sessionStorage.getItem(ACTIVE_DS_KEY) || 'null'); } catch {}
      if (!type && saved?.type) type = saved.type;

      if (!type) {
        bar.classList.add('hidden');
        return;
      }

      // 展示条
      bar.classList.remove('hidden');

      const displayLabel = saved?.label || ({
        MYSQL: 'MySQL 数据库',
        DM8: 'DM8 (达梦数据库)',
        POSTGRESQL: 'PostgreSQL',
        API: '外部API'
      }[type] || type);

      label.textContent = displayLabel;
      urlEl.textContent = saved?.url || ''; // 若为空，稍后 /info 会补上
      badge.className = 'badge bg-warning text-dark';
      badge.textContent = '检测中…';

      // 1) 连通性
      fetch(`/api/datasources/${encodeURIComponent(type)}/status`, { headers: { 'Accept': 'application/json' } })
        .then(r => r.json())
        .then(data => {
          if (!data || data.configured === false) {
            badge.className = 'badge bg-secondary';
            badge.textContent = '未配置';
          } else if (data.ok) {
            badge.className = 'badge bg-success';
            badge.textContent = '已连接';
          } else {
            badge.className = 'badge bg-danger';
            badge.textContent = data.message ? ('连接失败: ' + String(data.message).slice(0, 30)) : '连接失败';
          }
        })
        .catch(() => {
          badge.className = 'badge bg-danger';
          badge.textContent = '检测异常';
        });

      // 2) 取URL/池名（可选后端 /info），没有也不报错
      fetch(`/api/datasources/${encodeURIComponent(type)}/info`, { headers: { 'Accept': 'application/json' } })
        .then(r => r.ok ? r.json() : null)
        .then(info => {
          if (!info) return;
          if (info.url && !saved?.url) {
            urlEl.textContent = info.url;
            // 回写 sessionStorage，提升展示体验
            try {
              sessionStorage.setItem(ACTIVE_DS_KEY, JSON.stringify({
                ...(saved || { type, label: displayLabel }),
                url: info.url
              }));
            } catch {}
          }
        })
        .catch(() => {});
    }


    // 重置测试数据库
    async function resetDataBase() {
        const btn = document.getElementById('resetDataBase');
        if (!btn) return;

        // 1) 二次确认
        if (!confirm('确定要重置测试数据库吗？这会用主库数据覆盖临时库（dynamic_data_service_sandbox）。')) {
            return;
        }

        // 2) UI状态
        showLoading(true);
        btn.disabled = true;
        const originalText = btn.innerHTML;
        btn.innerHTML = '<i class="fas fa-sync fa-spin"></i> 重置中...';

        // 3) 工具：超时 + 轮询
        const timeoutMs = 2 * 60 * 1000; // 120秒超时
        const sleep = (ms) => new Promise(r => setTimeout(r, ms));
        const fetchWithTimeout = async (url, options = {}, ms = timeoutMs) => {
            const controller = new AbortController();
            const t = setTimeout(() => controller.abort(), ms);
            try {
                return await fetch(url, { ...options, signal: controller.signal });
            } finally {
                clearTimeout(t);
            }
        };

        // 4) 调用重置接口
        try {
            const resp = await fetchWithTimeout('/api/admin/sandbox/reset', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ reason: 'ui-reset' }) // 可选
            });

            // 4.1 同步完成：200/201
            if (resp.ok && resp.status !== 202) {
                const result = await resp.json().catch(() => ({}));
                if (result && result.success) {
                    showNotification(result.message || '测试数据库已成功重置。', 'success');
                } else {
                    showNotification(result.message || '重置完成，但未返回明确结果。', 'info');
                }
                return;
            }

            // 4.2 异步任务：202 + jobId -> 轮询进度
            if (resp.status === 202) {
                const payload = await resp.json().catch(() => ({}));
                const jobId = payload.jobId;
                if (!jobId) throw new Error('后端未返回 jobId，无法轮询任务状态。');

                // 轮询：最多 30 次，指数退避（1s, 1.5s, 2s...）
                let tries = 0;
                let delay = 1000;
                while (tries < 30) {
                    await sleep(delay);
                    tries += 1;
                    delay = Math.min(Math.floor(delay * 1.5), 5000); // 逐步增加至 5s

                    const s = await fetchWithTimeout(`/api/admin/sandbox/reset/${encodeURIComponent(jobId)}/status`, {
                        method: 'GET',
                        headers: { 'Accept': 'application/json' }
                    }, timeoutMs);

                    if (!s.ok) continue;
                    const status = await s.json().catch(() => ({}));
                    if (status && status.done) {
                        if (status.success) {
                            showNotification(status.message || '测试数据库已成功重置。', 'success');
                        } else {
                            showNotification(status.message || '重置失败。', 'error');
                        }
                        return;
                    }
                }
                // 超过轮询次数
                showNotification('重置操作已提交，但长时间未完成，请稍后在日志中确认状态。', 'warning');
                return;
            }

            // 4.3 其它HTTP状态
            const text = await resp.text().catch(() => '');
            throw new Error(text || `重置接口响应异常（HTTP ${resp.status}）`);

        } catch (err) {
            showNotification(`重置失败：${err.message || err}`, 'error');
        } finally {
            // 5) 恢复UI
            showLoading(false);
            btn.disabled = false;
            btn.innerHTML = originalText;
        }
    }


    initializeApp();
    
    // 绑定模态框事件
    document.getElementById('executeGraphqlBtn').addEventListener('click', executeGraphQLQuery);
    document.getElementById('resetQueryBtn').addEventListener('click', resetGraphQLQuery);
    document.getElementById('resetDataBase').addEventListener('click', resetDataBase);

});