-- === Online Education Demo Schema (MySQL 8.0 compatible) ===
-- Idempotent for tables via IF NOT EXISTS. Indexes are defined inline.

CREATE TABLE IF NOT EXISTS instructors (
  instructor_id INT PRIMARY KEY COMMENT '讲师ID',
  name          VARCHAR(100) NOT NULL COMMENT '讲师姓名',
  email         VARCHAR(120) NOT NULL COMMENT '讲师邮箱',
  city          VARCHAR(100) NOT NULL COMMENT '所在城市',
  hire_date     DATE NOT NULL COMMENT '入职日期'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='讲师信息表';

CREATE TABLE IF NOT EXISTS courses (
  course_id     INT PRIMARY KEY COMMENT '课程ID',
  title         VARCHAR(200) NOT NULL COMMENT '课程标题',
  category      VARCHAR(50)  NOT NULL COMMENT '课程类别',
  level         VARCHAR(20)  NOT NULL COMMENT '难度级别（初级/中级/高级）',
  price         DECIMAL(10,2) NOT NULL COMMENT '价格（元）',
  instructor_id INT NOT NULL COMMENT '授课讲师ID',
  created_at    DATE NOT NULL COMMENT '上线日期',
  CONSTRAINT fk_courses_instructor FOREIGN KEY (instructor_id) REFERENCES instructors(instructor_id),
  KEY idx_courses_category (category),
  KEY idx_courses_instructor (instructor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程信息表';

CREATE TABLE IF NOT EXISTS students (
  student_id    INT PRIMARY KEY COMMENT '学员ID',
  name          VARCHAR(100) NOT NULL COMMENT '学员姓名',
  email         VARCHAR(120) NOT NULL COMMENT '学员邮箱',
  city          VARCHAR(100) NOT NULL COMMENT '所在城市',
  signup_date   DATE NOT NULL COMMENT '注册日期',
  KEY idx_students_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员信息表';

CREATE TABLE IF NOT EXISTS enrollments (
  enrollment_id INT PRIMARY KEY COMMENT '报名ID',
  student_id    INT NOT NULL COMMENT '学员ID',
  course_id     INT NOT NULL COMMENT '课程ID',
  enrolled_at   DATE NOT NULL COMMENT '报名日期',
  status        VARCHAR(20) NOT NULL COMMENT '报名状态（active/completed/refunded/canceled）',
  CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) REFERENCES students(student_id),
  CONSTRAINT fk_enrollments_course  FOREIGN KEY (course_id)  REFERENCES courses(course_id),
  KEY idx_enrollments_student (student_id),
  KEY idx_enrollments_course  (course_id),
  KEY idx_enrollments_status  (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名关系表';

CREATE TABLE IF NOT EXISTS lessons (
  lesson_id     INT PRIMARY KEY COMMENT '课时ID',
  course_id     INT NOT NULL COMMENT '课程ID',
  seq_no        INT NOT NULL COMMENT '课时序号',
  title         VARCHAR(200) NOT NULL COMMENT '课时标题',
  duration_min  INT NOT NULL COMMENT '时长（分钟）',
  CONSTRAINT fk_lessons_course FOREIGN KEY (course_id) REFERENCES courses(course_id),
  KEY idx_lessons_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课时信息表';

CREATE TABLE IF NOT EXISTS progress (
  progress_id   INT PRIMARY KEY COMMENT '进度ID',
  enrollment_id INT NOT NULL COMMENT '报名ID',
  lesson_id     INT NOT NULL COMMENT '课时ID',
  completed     BOOLEAN NOT NULL COMMENT '是否完成',
  completed_at  DATE COMMENT '完成日期',
  CONSTRAINT fk_progress_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(enrollment_id),
  CONSTRAINT fk_progress_lesson     FOREIGN KEY (lesson_id)     REFERENCES lessons(lesson_id),
  KEY idx_progress_enrollment (enrollment_id),
  KEY idx_progress_lesson     (lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习进度表';

CREATE TABLE IF NOT EXISTS payments (
  payment_id    INT PRIMARY KEY COMMENT '支付ID',
  enrollment_id INT NOT NULL COMMENT '报名ID',
  amount        DECIMAL(10,2) NOT NULL COMMENT '金额',
  currency      VARCHAR(10) NOT NULL COMMENT '币种',
  method        VARCHAR(20) NOT NULL COMMENT '支付方式（card/wechat/alipay/paypal）',
  status        VARCHAR(20) NOT NULL COMMENT '支付状态（paid/refunded/failed）',
  paid_at       DATE COMMENT '支付时间',
  CONSTRAINT fk_payments_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(enrollment_id),
  KEY idx_payments_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';