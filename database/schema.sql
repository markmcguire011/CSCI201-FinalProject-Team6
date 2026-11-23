-- USC Marketplace: database tables (section 15, Saved Data)
-- Run this once in MySQL Workbench after creating the usc_marketplace database
-- (Deployment document, Database setup, steps 2 and 3).
-- Every table uses CREATE TABLE IF NOT EXISTS, so running it again does not delete any data.

USE usc_marketplace;

-- Accounts. Only @usc.edu emails, one account per email.
CREATE TABLE IF NOT EXISTS users (
  user_id             INT AUTO_INCREMENT PRIMARY KEY,
  full_name           VARCHAR(50)  NOT NULL,
  email               VARCHAR(254) NOT NULL,
  password_hash       VARCHAR(255) NOT NULL,
  account_type        ENUM('STUDENT', 'ALUMNI') NOT NULL,
  role                ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
  verified            BOOLEAN      NOT NULL DEFAULT FALSE,
  bio                 VARCHAR(300),
  photo_path          VARCHAR(255),
  failed_login_count  INT          NOT NULL DEFAULT 0,
  locked_until        DATETIME,
  suspended_until     DATETIME,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_users_email UNIQUE (email),
  CONSTRAINT chk_users_usc_email CHECK (email LIKE '%@usc.edu')
);

-- One active 6-digit code per user.
CREATE TABLE IF NOT EXISTS verification_codes (
  user_id       INT PRIMARY KEY,
  code          CHAR(6)  NOT NULL,
  expires_at    DATETIME NOT NULL,
  last_sent_at  DATETIME NOT NULL,
  CONSTRAINT fk_codes_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS listings (
  listing_id          INT AUTO_INCREMENT PRIMARY KEY,
  seller_id           INT           NOT NULL,
  title               VARCHAR(80)   NOT NULL,
  category            VARCHAR(30)   NOT NULL,
  price               INT           NOT NULL,
  item_condition      VARCHAR(20)   NOT NULL,
  course_code         VARCHAR(20),
  pickup_spot         VARCHAR(60)   NOT NULL,
  description         VARCHAR(1000) NOT NULL,
  status              ENUM('AVAILABLE', 'PENDING', 'SOLD', 'INACTIVE', 'EXPIRED') NOT NULL DEFAULT 'AVAILABLE',
  buyer_id            INT,
  payment_proof_path  VARCHAR(255),
  deleted_by_admin    BOOLEAN  NOT NULL DEFAULT FALSE,
  version             INT      NOT NULL DEFAULT 1,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  last_activity_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_listings_price CHECK (price BETWEEN 0 AND 10000),
  CONSTRAINT fk_listings_seller FOREIGN KEY (seller_id) REFERENCES users (user_id),
  CONSTRAINT fk_listings_buyer  FOREIGN KEY (buyer_id)  REFERENCES users (user_id),
  INDEX idx_listings_feed   (status, created_at),
  INDEX idx_listings_seller (seller_id),
  INDEX idx_listings_filter (category, price)
);

-- Up to 5 photos per listing, shown in sort_order.
CREATE TABLE IF NOT EXISTS listing_photos (
  photo_id    INT AUTO_INCREMENT PRIMARY KEY,
  listing_id  INT          NOT NULL,
  file_path   VARCHAR(255) NOT NULL,
  sort_order  TINYINT      NOT NULL,
  CONSTRAINT chk_photos_order CHECK (sort_order BETWEEN 1 AND 5),
  CONSTRAINT uq_photos_order UNIQUE (listing_id, sort_order),
  CONSTRAINT fk_photos_listing FOREIGN KEY (listing_id) REFERENCES listings (listing_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS saved_listings (
  user_id     INT      NOT NULL,
  listing_id  INT      NOT NULL,
  saved_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, listing_id),
  CONSTRAINT fk_saved_user    FOREIGN KEY (user_id)    REFERENCES users (user_id),
  CONSTRAINT fk_saved_listing FOREIGN KEY (listing_id) REFERENCES listings (listing_id)
);

-- One conversation per buyer per listing.
CREATE TABLE IF NOT EXISTS conversations (
  conversation_id  INT AUTO_INCREMENT PRIMARY KEY,
  listing_id       INT      NOT NULL,
  buyer_id         INT      NOT NULL,
  seller_id        INT      NOT NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_message_at  DATETIME,
  CONSTRAINT uq_conversations UNIQUE (listing_id, buyer_id),
  CONSTRAINT fk_conv_listing FOREIGN KEY (listing_id) REFERENCES listings (listing_id),
  CONSTRAINT fk_conv_buyer   FOREIGN KEY (buyer_id)   REFERENCES users (user_id),
  CONSTRAINT fk_conv_seller  FOREIGN KEY (seller_id)  REFERENCES users (user_id)
);

CREATE TABLE IF NOT EXISTS messages (
  message_id       INT AUTO_INCREMENT PRIMARY KEY,
  conversation_id  INT           NOT NULL,
  sender_id        INT           NOT NULL,
  body             VARCHAR(1000) NOT NULL,
  sent_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_read          BOOLEAN       NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_messages_conv   FOREIGN KEY (conversation_id) REFERENCES conversations (conversation_id),
  CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id)       REFERENCES users (user_id),
  INDEX idx_messages_history (conversation_id, sent_at)
);

-- One review per sale (listing_id is unique).
CREATE TABLE IF NOT EXISTS reviews (
  review_id         INT AUTO_INCREMENT PRIMARY KEY,
  listing_id        INT      NOT NULL,
  reviewer_id       INT      NOT NULL,
  reviewed_user_id  INT      NOT NULL,
  stars             TINYINT  NOT NULL,
  comment           VARCHAR(500),
  anonymous         BOOLEAN  NOT NULL DEFAULT FALSE,
  deleted_by_admin  BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_reviews_listing UNIQUE (listing_id),
  CONSTRAINT chk_reviews_stars CHECK (stars BETWEEN 1 AND 5),
  CONSTRAINT fk_reviews_listing  FOREIGN KEY (listing_id)       REFERENCES listings (listing_id),
  CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id)      REFERENCES users (user_id),
  CONSTRAINT fk_reviews_reviewed FOREIGN KEY (reviewed_user_id) REFERENCES users (user_id)
);

-- One report per user per target.
CREATE TABLE IF NOT EXISTS reports (
  report_id     INT AUTO_INCREMENT PRIMARY KEY,
  reporter_id   INT NOT NULL,
  target_type   ENUM('LISTING', 'USER') NOT NULL,
  target_id     INT NOT NULL,
  reason        ENUM('SCAM', 'PROHIBITED_ITEM', 'OFFENSIVE', 'OTHER') NOT NULL,
  comment       VARCHAR(300),
  status        ENUM('OPEN', 'RESOLVED') NOT NULL DEFAULT 'OPEN',
  handled_by    INT,
  action_taken  VARCHAR(30),
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at   DATETIME,
  CONSTRAINT uq_reports UNIQUE (reporter_id, target_type, target_id),
  CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (user_id),
  CONSTRAINT fk_reports_admin    FOREIGN KEY (handled_by)  REFERENCES users (user_id)
);

CREATE TABLE IF NOT EXISTS notifications (
  notification_id  INT AUTO_INCREMENT PRIMARY KEY,
  user_id          INT          NOT NULL,
  type             VARCHAR(30)  NOT NULL,
  text             VARCHAR(300) NOT NULL,
  link             VARCHAR(255),
  is_read          BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
