package com.example.dynamicdata.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 * 
 * 该类使用Spring的@ControllerAdvice注解，提供应用程序级别的统一异常处理机制。
 * 主要功能是捕获和处理各种类型的异常，确保向客户端返回统一格式的错误响应，
 * 同时避免敏感信息（如数据库连接信息、内部实现细节等）泄露给最终用户。
 * 
 * 核心功能：
 * 1. 运行时异常处理 - 处理SQL异常、Schema配置异常等运行时错误
 * 2. 参数验证异常处理 - 处理非法参数和输入验证失败
 * 3. 通用异常处理 - 兜底处理所有未被特定处理器捕获的异常
 * 4. 统一错误响应格式 - 确保所有错误响应具有一致的JSON结构
 * 5. 安全信息过滤 - 防止敏感系统信息暴露给客户端
 * 
 * 异常处理策略：
 * - 分层异常处理：按异常类型的具体程度进行分层处理
 * - 用户友好消息：将技术异常转换为用户可理解的错误描述
 * - 详细日志记录：记录完整的异常堆栈信息供开发人员调试
 * - 安全信息隐藏：避免向客户端暴露内部实现细节
 * 
 * 错误响应格式：
 * {
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 500,
 *   "error": "错误类型描述",
 *   "message": "用户友好的错误消息",
 *   "path": "请求路径"
 * }
 * 
 * 安全考虑：
 * - 不在响应中包含异常堆栈信息
 * - 不暴露数据库连接字符串或SQL语句
 * - 不显示内部文件路径或系统配置信息
 * - 对敏感异常信息进行脱敏处理
 * 
 * 使用场景：
 * - GraphQL查询执行异常处理
 * - 数据库操作异常处理
 * - 业务逻辑验证异常处理
 * - 系统配置和初始化异常处理
 * 
 * @author Dynamic Data Service Team
 * @version 1.0
 * @since 2024-01-15
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 日志记录器
     * 
     * 用于记录异常处理过程中的详细信息，包括：
     * - 异常的完整堆栈信息（仅记录到日志，不返回给客户端）
     * - 异常发生的上下文信息（请求路径、参数等）
     * - 异常处理的执行流程和决策过程
     * 
     * 日志级别使用策略：
     * - ERROR: 系统级异常和未预期的运行时异常
     * - WARN: 业务逻辑异常和参数验证失败
     * - INFO: 正常的异常处理流程记录
     * - DEBUG: 详细的异常分析和调试信息
     */
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理运行时异常
     * 
     * 该方法专门处理RuntimeException及其子类异常，这类异常通常表示程序运行过程中
     * 遇到的不可预期问题，如SQL执行异常、GraphQL Schema配置错误、业务逻辑异常等。
     * 
     * 异常分类处理：
     * 1. SQL相关异常 - 数据库连接、查询执行、事务处理等问题
     * 2. Schema相关异常 - GraphQL Schema定义、解析器配置等问题
     * 3. 其他运行时异常 - 业务逻辑、系统配置等其他运行时问题
     * 
     * 处理流程：
     * 1. 记录完整的异常信息到日志（包含堆栈跟踪）
     * 2. 根据异常消息内容判断异常类型
     * 3. 生成用户友好的错误消息（隐藏技术细节）
     * 4. 构建标准格式的错误响应
     * 5. 返回HTTP 500状态码
     * 
     * 安全措施：
     * - 不在响应中包含原始异常消息（可能包含敏感信息）
     * - 不暴露数据库连接信息或SQL语句内容
     * - 将技术异常转换为业务层面的错误描述
     * 
     * 错误消息映射：
     * - 包含"SQL"的异常 → "数据查询失败，请检查查询参数"
     * - 包含"Schema"的异常 → "Schema配置错误，请联系管理员"
     * - 其他异常 → "操作失败，请稍后重试"
     * - 空消息异常 → "系统内部错误"
     * 
     * @param ex 运行时异常实例，包含异常的详细信息和堆栈跟踪
     * @param request Web请求上下文，用于获取请求路径等信息
     * @return ResponseEntity包装的错误响应，包含时间戳、状态码、错误类型、用户消息和请求路径
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        // 记录完整的异常信息到日志，包含堆栈跟踪，供开发人员调试使用
        logger.error("运行时异常发生 - 异常类型: {}, 异常消息: {}", 
                    ex.getClass().getSimpleName(), ex.getMessage(), ex);
        
        // 构建标准格式的错误响应对象
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now()); // 异常发生的精确时间
        errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value()); // HTTP状态码500
        errorResponse.put("error", "内部服务器错误"); // 错误类型的标准描述
        
        // 根据异常消息内容智能判断异常类型，提供用户友好的错误信息
        // 这里进行异常消息的分类和脱敏处理，避免暴露技术细节
        if (ex.getMessage() != null) {
            String exceptionMessage = ex.getMessage().toLowerCase();
            
            if (exceptionMessage.contains("sql") || exceptionMessage.contains("database") || 
                exceptionMessage.contains("connection")) {
                // SQL和数据库相关异常的用户友好提示
                errorResponse.put("message", "数据查询失败，请检查查询参数");
                logger.warn("数据库操作异常被捕获并转换为用户友好消息");
            } else if (exceptionMessage.contains("schema") || exceptionMessage.contains("graphql") ||
                      exceptionMessage.contains("resolver")) {
                // GraphQL Schema和解析器相关异常的用户友好提示
                errorResponse.put("message", "Schema配置错误，请联系管理员");
                logger.warn("GraphQL Schema配置异常被捕获并转换为用户友好消息");
            } else {
                // 其他类型运行时异常的通用提示
                errorResponse.put("message", "操作失败，请稍后重试");
                logger.warn("通用运行时异常被捕获: {}", ex.getClass().getSimpleName());
            }
        } else {
            // 异常消息为空的情况处理
            errorResponse.put("message", "系统内部错误");
            logger.error("捕获到空消息的运行时异常: {}", ex.getClass().getSimpleName());
        }
        
        // 添加请求路径信息，帮助定位问题发生的具体接口
        errorResponse.put("path", request.getDescription(false));
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 处理非法参数异常
     * 
     * 该方法专门处理IllegalArgumentException异常，这类异常通常由以下情况引起：
     * - 方法参数值不符合预期范围或格式
     * - 业务逻辑验证失败（如ID不存在、状态不合法等）
     * - 输入数据格式错误或类型转换失败
     * - GraphQL查询参数验证失败
     * 
     * 与RuntimeException的区别：
     * - IllegalArgumentException通常表示客户端输入错误，返回400状态码
     * - RuntimeException通常表示服务端内部错误，返回500状态码
     * 
     * 处理策略：
     * 1. 使用WARN级别记录日志（非系统错误，而是输入问题）
     * 2. 返回HTTP 400 Bad Request状态码
     * 3. 提供明确的参数错误提示信息
     * 4. 不暴露具体的参数验证逻辑细节
     * 
     * 常见触发场景：
     * - GraphQL查询参数类型错误
     * - 数据库ID参数为负数或超出范围
     * - 枚举值参数不在允许范围内
     * - 必填参数为空或null
     * - 字符串参数长度超出限制
     * 
     * 安全考虑：
     * - 不在响应中包含具体的参数值（可能包含敏感信息）
     * - 不暴露参数验证的具体规则和逻辑
     * - 提供通用的参数错误提示，避免信息泄露
     * 
     * @param ex 非法参数异常实例，包含参数验证失败的详细信息
     * @param request Web请求上下文，用于获取请求路径和参数信息
     * @return ResponseEntity包装的错误响应，状态码为400，包含参数错误的友好提示
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        // 使用WARN级别记录参数异常，这通常是客户端输入问题而非系统错误
        logger.warn("非法参数异常被捕获 - 请求路径: {}, 异常消息: {}", 
                   request.getDescription(false), ex.getMessage());
        
        // 构建参数错误的标准响应格式
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now()); // 异常发生时间
        errorResponse.put("status", HttpStatus.BAD_REQUEST.value()); // HTTP 400状态码
        errorResponse.put("error", "请求参数错误"); // 错误类型标识
        
        // 提供用户友好的参数错误提示，不暴露具体的验证规则
        errorResponse.put("message", "请求参数不合法，请检查输入参数的格式和取值范围");
        
        // 记录请求路径，帮助客户端定位具体的问题接口
        errorResponse.put("path", request.getDescription(false));
        
        // 记录参数异常的处理结果
        logger.info("参数异常已转换为用户友好的400响应");
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理静态资源未找到异常
     * 特别处理开发工具相关的资源请求（如 @vite/client），避免产生错误日志
     * 
     * @param ex NoResourceFoundException实例
     * @param request Web请求上下文
     * @return ResponseEntity包装的404响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFoundException(
            NoResourceFoundException ex, WebRequest request) {
        
        String requestPath = request.getDescription(false);
        
        // 对于开发工具相关的资源请求，使用DEBUG级别日志，避免污染错误日志
        if (requestPath.contains("@vite") || requestPath.contains("__vite") || 
            requestPath.contains(".vite") || requestPath.contains("vite")) {
            logger.debug("开发工具资源请求被忽略: {}", requestPath);
        } else {
            // 其他静态资源请求使用WARN级别
            logger.warn("静态资源未找到 - 请求路径: {}, 资源: {}", 
                       requestPath, ex.getResourcePath());
        }
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.NOT_FOUND.value());
        errorResponse.put("error", "资源未找到");
        errorResponse.put("message", "请求的资源不存在");
        errorResponse.put("path", requestPath);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * 处理通用异常（兜底异常处理器）
     * 
     * 该方法作为最后的异常处理兜底机制，捕获所有未被上述特定异常处理器处理的异常。
     * 这包括但不限于：检查异常、自定义业务异常、第三方库异常、系统级异常等。
     * 
     * 兜底处理的重要性：
     * - 确保任何异常都不会导致应用程序崩溃
     * - 保证客户端始终能收到格式化的错误响应
     * - 防止异常信息直接暴露给最终用户
     * - 提供统一的错误处理和日志记录机制
     * 
     * 可能捕获的异常类型：
     * 1. IOException - 文件操作、网络通信异常
     * 2. 自定义业务异常 - 应用程序特定的业务逻辑异常
     * 3. 第三方库异常 - JSON解析、HTTP客户端、数据库驱动等异常
     * 4. 系统异常 - 内存不足、线程异常等系统级问题
     * 5. 未分类的RuntimeException子类
     * 
     * 处理策略：
     * 1. 使用ERROR级别记录完整异常信息（包含堆栈跟踪）
     * 2. 返回HTTP 500状态码（服务器内部错误）
     * 3. 提供通用的系统错误提示
     * 4. 完全隐藏异常的技术细节
     * 
     * 安全措施：
     * - 绝不在响应中包含异常堆栈信息
     * - 不暴露异常的具体类型和消息
     * - 使用统一的通用错误消息
     * - 详细信息仅记录到服务器日志中
     * 
     * 监控和告警：
     * - 该处理器被触发通常表示存在未预期的系统问题
     * - 建议对此类异常设置监控告警
     * - 定期分析此类异常的模式和频率
     * 
     * @param ex 通用异常实例，可能是任何类型的异常
     * @param request Web请求上下文，用于获取请求相关信息
     * @return ResponseEntity包装的通用错误响应，状态码为500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {
        
        // 使用ERROR级别记录未处理的异常，这通常表示系统存在未预期的问题
        logger.error("捕获到未分类的异常 - 异常类型: {}, 请求路径: {}, 异常消息: {}", 
                    ex.getClass().getName(), request.getDescription(false), ex.getMessage(), ex);
        
        // 构建通用系统错误的标准响应格式
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now()); // 异常发生的精确时间
        errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value()); // HTTP 500状态码
        errorResponse.put("error", "系统错误"); // 通用错误类型标识
        
        // 提供最通用的错误提示，完全隐藏技术细节
        errorResponse.put("message", "系统发生未知错误，请联系管理员");
        
        // 记录请求路径，帮助管理员定位问题
        errorResponse.put("path", request.getDescription(false));
        
        // 记录兜底异常处理器的触发情况，用于系统监控
        logger.error("兜底异常处理器被触发，建议检查系统状态和异常模式");
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}