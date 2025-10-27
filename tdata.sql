-- MySQL import helpers
SET NAMES utf8mb4; SET FOREIGN_KEY_CHECKS=0;

-- === Online Education Demo Data ===

-- Instructors
INSERT INTO instructors (instructor_id, name, email, city, hire_date) VALUES
(1, 'Alice Wang',   'alice.wang@example.com',   '上海', '2024-06-01'),
(2, 'Bob Li',       'bob.li@example.com',       '北京', '2024-08-15'),
(3, 'Carol Zhang',  'carol.zhang@example.com',  '深圳', '2025-01-10');

-- Courses
INSERT INTO courses (course_id, title, category, level, price, instructor_id, created_at) VALUES
(101, 'Python 数据分析入门',        'Data',     'beginner',     199.00, 1, '2025-08-20'),
(102, '机器学习实战（Scikit‑Learn）', 'AI',       'intermediate', 399.00, 1, '2025-09-01'),
(103, '前端开发：React + Vite',     'Web',      'beginner',     299.00, 2, '2025-09-10'),
(104, '数据库系统与SQL优化',        'Backend',  'advanced',     499.00, 2, '2025-09-18'),
(105, '深度学习入门（PyTorch）',    'AI',       'beginner',     359.00, 3, '2025-10-01');

-- Students
INSERT INTO students (student_id, name, email, city, signup_date) VALUES
(1001, '张伟',  'zhang.wei@example.com',  '上海', '2025-09-12'),
(1002, '李娜',  'li.na@example.com',      '北京', '2025-09-15'),
(1003, '王强',  'wang.qiang@example.com', '深圳', '2025-09-22'),
(1004, '赵敏',  'zhao.min@example.com',   '杭州', '2025-09-25'),
(1005, '陈杰',  'chen.jie@example.com',   '广州', '2025-10-02'),
(1006, '刘洋',  'liu.yang@example.com',   '南京', '2025-10-04');

-- Enrollments
INSERT INTO enrollments (enrollment_id, student_id, course_id, enrolled_at, status) VALUES
(5001, 1001, 101, '2025-09-20', 'active'),
(5002, 1002, 101, '2025-09-21', 'completed'),
(5003, 1003, 103, '2025-09-26', 'active'),
(5004, 1001, 102, '2025-09-28', 'canceled'),
(5005, 1004, 103, '2025-10-01', 'active'),
(5006, 1005, 104, '2025-10-03', 'active'),
(5007, 1006, 105, '2025-10-05', 'active'),
(5008, 1002, 104, '2025-10-06', 'refunded'),
(5009, 1003, 102, '2025-10-07', 'active'),
(5010, 1004, 105, '2025-10-08', 'active');

-- Lessons
INSERT INTO lessons (lesson_id, course_id, seq_no, title, duration_min) VALUES
(9001, 101, 1, '环境安装与Jupyter', 30),
(9002, 101, 2, 'Pandas 基础',       45),
(9003, 101, 3, '可视化入门',         35),
(9004, 102, 1, '监督学习概览',       40),
(9005, 102, 2, '特征工程',           50),
(9006, 103, 1, 'Vite 项目初始化',    25),
(9007, 103, 2, 'React 组件基础',     40),
(9008, 104, 1, '索引与执行计划',     55),
(9009, 105, 1, 'Tensor 基础',        35),
(9010, 105, 2, '自动微分',           45);

-- Progress (some completed flags)
INSERT INTO progress (progress_id, enrollment_id, lesson_id, completed, completed_at) VALUES
(7001, 5001, 9001, TRUE,  '2025-09-21'),
(7002, 5001, 9002, FALSE, NULL),
(7003, 5002, 9001, TRUE,  '2025-09-21'),
(7004, 5002, 9002, TRUE,  '2025-09-22'),
(7005, 5002, 9003, TRUE,  '2025-09-23'),
(7006, 5003, 9006, TRUE,  '2025-09-27'),
(7007, 5003, 9007, FALSE, NULL),
(7008, 5005, 9006, TRUE,  '2025-10-02'),
(7009, 5006, 9008, FALSE, NULL),
(7010, 5007, 9009, TRUE,  '2025-10-06');

-- Payments
INSERT INTO payments (payment_id, enrollment_id, amount, currency, method, status, paid_at) VALUES
(8001, 5001, 199.00, 'CNY', 'alipay', 'paid',     '2025-09-20'),
(8002, 5002, 199.00, 'CNY', 'wechat', 'paid',     '2025-09-21'),
(8003, 5003, 299.00, 'CNY', 'card',   'paid',     '2025-09-26'),
(8004, 5004, 399.00, 'CNY', 'alipay', 'failed',   NULL),
(8005, 5005, 299.00, 'CNY', 'wechat', 'paid',     '2025-10-01'),
(8006, 5006, 499.00, 'CNY', 'card',   'paid',     '2025-10-03'),
(8007, 5007, 359.00, 'CNY', 'alipay', 'paid',     '2025-10-05'),
(8008, 5008, 499.00, 'CNY', 'card',   'refunded', '2025-10-06'),
(8009, 5009, 399.00, 'CNY', 'wechat', 'paid',     '2025-10-07'),
(8010, 5010, 359.00, 'CNY', 'alipay', 'paid',     '2025-10-08');

SET FOREIGN_KEY_CHECKS=1;
