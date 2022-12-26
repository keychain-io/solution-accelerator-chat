







CREATE TABLE settings (
	id		       INTEGER	PRIMARY KEY AUTOINCREMENT,
	name	              TEXT		NOT NULL UNIQUE,
	value	              TEXT		NOT NULL UNIQUE
	);

INSERT INTO settings VALUES (1, "version", "1");








CREATE TABLE cache_status (
       id			INTEGER	PRIMARY KEY CHECK (id=1),
       last_checked_height	INTEGER	DEFAULT 0 CHECK(last_checked_height>=0),
       last_block_height	INTEGER	DEFAULT NULL,
       last_block_hash	TEXT	       DEFAULT NULL,
	last_update_time	INTEGER	DEFAULT NULL,
       
       active_persona       INTEGER       DEFAULT NULL REFERENCES personas(id) ON DELETE SET NULL ON UPDATE RESTRICT
       );

INSERT INTO cache_status VALUES (1, 0, NULL, NULL, NULL, NULL);







CREATE TABLE IF NOT EXISTS pubkey_type (
       key_type_id	       TEXT		PRIMARY KEY NOT NULL,
       key_type_num	       INTEGER 	NOT NULL
       );


INSERT INTO pubkey_type VALUES ("encryption", 0), ("signature", 1);









CREATE TABLE IF NOT EXISTS key_pairs (
       id			INTEGER	PRIMARY KEY AUTOINCREMENT,
       public_key	       TEXT		NOT NULL,
       private_key	       TEXT		NOT NULL,
       key_type		TEXT		NOT NULL REFERENCES pubkey_type(key_type_id),
       create_time	       INTEGER	NOT NULL 
);










CREATE TABLE IF NOT EXISTS addresses (
       address		TEXT		PRIMARY KEY,
       is_multisig	       INTEGER	NOT NULL DEFAULT 0,
       m			INTEGER,
       n			INTEGER,
       is_segwit	       INTEGER	NOT NULL DEFAULT 0,
       is_deposit	       INTEGER	NOT NULL DEFAULT 0,
       create_time	       INTEGER	NOT NULL ,
       owner_id             INTEGER       REFERENCES personas(id) ON DELETE CASCADE ON UPDATE RESTRICT,

       CONSTRAINT uc_multisig	CHECK(is_multisig=0 OR (m IS NOT NULL AND n IS NOT NULL))
       );









CREATE TABLE IF NOT EXISTS wallet (
       id			INTEGER	PRIMARY KEY AUTOINCREMENT,
       address		TEXT		NOT NULL REFERENCES addresses(address),
       private_key	       TEXT		NOT NULL UNIQUE,
       public_key	       TEXT		NOT NULL UNIQUE,
       hd_index		INTEGER	NOT NULL UNIQUE CHECK(hd_index>=0),
       create_time	       INTEGER	NOT NULL 
       );








CREATE TABLE IF NOT EXISTS utxos (
       id			INTEGER	PRIMARY KEY AUTOINCREMENT,
       txid			TEXT		NOT NULL,
       vout			INTEGER	NOT NULL CHECK(vout>=0),
       amount		       INTEGER	NOT NULL CHECK(amount>=0),
       num_confs	       INTEGER	NOT NULL DEFAULT 0 CHECK(num_confs>=0),
       spendable	       INTEGER  	NOT NULL CHECK(spendable=0 OR spendable=1),
	address		TEXT		NOT NULL REFERENCES addresses(address) ON DELETE CASCADE ON UPDATE RESTRICT, -- reference constraint added 14/11/2019
	output_height        INTEGER	NOT NULL CHECK(output_height>=0),
       create_time	       INTEGER	NOT NULL ,
       last_mod_time	       INTEGER 	NOT NULL ,
      -- owner_id              INTEGER       NOT NULL REFERENCES personas(id) ON DELETE CASCADE ON UPDATE RESTRICT,

       CONSTRAINT uc_internal_utxos_unique
       		  UNIQUE (txid, vout)	   
       );









CREATE TABLE IF NOT EXISTS keychains (
       id			INTEGER	PRIMARY KEY AUTOINCREMENT,

       txid			TEXT		NOT NULL,
       link_vout		INTEGER	NOT NULL CHECK(link_vout>=0),
       address		TEXT		NOT NULL,
       payload_vout	       INTEGER	NOT NULL CHECK(payload_vout>=0),
       link_amount	       INTEGER	NOT NULL CHECK(link_amount>0),

       parent_id		INTEGER	REFERENCES keychains(id) ON DELETE CASCADE ON UPDATE RESTRICT,
       owner_id		INTEGER       REFERENCES personas(id) ON DELETE CASCADE ON UPDATE RESTRICT,
       --prev_address	TEXT,		-- is null iff is root of keychain
       --prev_txid       	TEXT,		-- is null iff is root of keychain
       --input_vout	INTEGER		CHECK(input_vout>=0), -- is null iff is root of keychain

       public_key	       TEXT		NOT NULL,
       key_type		TEXT 		NOT NULL REFERENCES pubkey_type(key_type_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
       algorithm            TEXT		NOT NULL,
       key_format           TEXT		NOT NULL,

       block_hash	       TEXT,
       block_height         INTEGER	CHECK(block_height>=0),
	expiration_height    INTEGER	CHECK(expiration_height>block_height+min_confs),
       is_mature       	INTEGER	NOT NULL DEFAULT 0 CHECK(is_mature=0 OR is_mature=1),
	num_confs	       INTEGER	NOT NULL DEFAULT 0 CHECK(num_confs>=0),
	min_confs	       INTEGER	NOT NULL DEFAULT 1 CHECK(min_confs>0),
       --is_final		INTEGER		NOT NULL DEFAULT 0,

       create_time	       INTEGER	NOT NULL ,
       last_mod_time	       INTEGER	NOT NULL ,
       sent_time		INTEGER	DEFAULT NULL,

       --CONSTRAINT uc_internal_keychain_root
       	--	  CHECK((is_root=0 AND prev_address IS NULL AND prev_txid IS NULL)
	--	  OR (is_root=1 AND prev_address IS NOT NULL AND prev_txid IS NOT NULL)),
       
       CONSTRAINT uc_keychain_payload UNIQUE (txid, payload_vout),
       
       CONSTRAINT uc_keychain_link UNIQUE (txid, link_vout),

       CONSTRAINT uc_keychain_vouts CHECK(payload_vout != link_vout)

       --FOREIGN KEY (prev_txid, input_vout)
       	--       REFERENCES keychains (txid, link_vout)
       	--        ON DELETE CASCADE ON UPDATE RESTRICT,


	--FOREIGN KEY (prev_txid, input_vout)
	--	REFERENCES utxos (txid, vout)
	--	ON DELETE RESTRICT ON UPDATE RESTRICT,


       --FOREIGN KEY (public_key, key_type)
       --	       REFERENCES key_pairs (public_key, key_type)
       --       ON DELETE RESTRICT on UPDATE RESTRICT
       );










CREATE TABLE IF NOT EXISTS personas (
       id			INTEGER	PRIMARY KEY AUTOINCREMENT,
       status               INTEGER       NOT NULL,

       uri                  TEXT          DEFAULT NULL,
       name			TEXT		NOT NULL CHECK(name <> ""),
       subname		TEXT		NOT NULL DEFAULT "",
       is_internal		INTEGER	NOT NULL CHECK(is_internal=0 OR is_internal=1),
       contact_owner	       INTEGER	DEFAULT NULL REFERENCES personas(id),
	security_level	INTEGER	DEFAULT NULL,

       encr_root_id		INTEGER	DEFAULT NULL REFERENCES keychains(id) ON DELETE SET NULL ON UPDATE RESTRICT,
       encr_tip_id		INTEGER	DEFAULT NULL REFERENCES keychains(id) ON DELETE SET NULL ON UPDATE RESTRICT,
       encr_is_final	       INTEGER	NOT NULL DEFAULT 0,
	encr_algorithm	INTEGER	DEFAULT  NULL,
	encr_key_size_bits	INTEGER	DEFAULT  NULL,

       sign_root_id		INTEGER	DEFAULT NULL REFERENCES keychains(id) ON DELETE SET NULL ON UPDATE RESTRICT,
       sign_tip_id		INTEGER	DEFAULT NULL REFERENCES keychains(id) ON DELETE SET NULL ON UPDATE RESTRICT,
       sign_is_final	       INTEGER	NOT NULL DEFAULT 0,
	sign_algorithm	INTEGER	DEFAULT  NULL,
	sign_key_size_bits	INTEGER	DEFAULT  NULL,
       --root_txid			INTEGER		NOT NULL,
       --root_link_vout		INTEGER		NOT NULL,
       create_time		INTEGER	NOT NULL ,

       --CONSTRAINT uc_personas UNIQUE (contact_owner, name, subname, is_internal),
       CONSTRAINT uc_owner_uri UNIQUE (contact_owner, uri),

       -- Removed NOT NULL constraint on keychain IDs on 15/11/2019 to allow creation of persona without funding or keychains
       --CONSTRAINT c_root_tip CHECK (is_internal=0 OR (encr_root_id IS NOT NULL AND
       --encr_tip_id IS NOT NULL AND sign_root_id IS NOT NULL AND sign_tip_id IS NOT NULL)),

	CONSTRAINT c_contact_persona CHECK ((is_internal=0 OR contact_owner IS NULL) AND
	(is_internal=1 OR contact_owner IS NOT NULL))
       --FOREIGN KEY (root_txid, root_link_vout)
       --	       REFERENCES keychains (txid, link_vout)
	--       ON DELETE CASCADE ON UPDATE CASCADE
       );









CREATE TABLE IF NOT EXISTS groups (
       id			INTEGER       PRIMARY KEY NOT NULL,
       name		       TEXT          NOT NULL,
       create_time	       INTEGER       NOT NULL 
       );









CREATE TABLE IF NOT EXISTS group_memberships (
       persona_id           INTEGER	NOT NULL REFERENCES personas(id) ON DELETE CASCADE ON UPDATE CASCADE,
       group_id		INTEGER	NOT NULL REFERENCES groups(id) ON DELETE CASCADE ON UPDATE CASCADE,

       CONSTRAINT uc_group_membership_unique UNIQUE (persona_id, group_id)

       );
       






--- Ledger transactions 
--- TODO: put in separate schema

CREATE TABLE IF NOT EXISTS assets (
       id                   INTEGER       PRIMARY KEY AUTOINCREMENT,
       symbol               TEXT          NOT NULL,
       issuer               TEXT          NOT NULL REFERENCES personas(uri) ON DELETE RESTRICT ON UPDATE RESTRICT,
       script               TEXT          NOT NULL,
       hash                 TEXT          NOT NULL,
       issuer_sig           TEXT          NOT NULL,
	issuer_sig_timestamp	INTEGER	DEFAULT NULL,
       version              TEXT          NOT NULL,

       CONSTRAINT uc_hash UNIQUE (hash),
       CONSTRAINT uc_symbol_issuer UNIQUE (symbol, issuer)
);








CREATE TABLE IF NOT EXISTS certificates (
       id                   INTEGER       PRIMARY KEY AUTOINCREMENT,
       holder_uri           TEXT          NOT NULL REFERENCES personas(uri) ON DELETE RESTRICT ON UPDATE RESTRICT,
       asset_hash           TEXT          NOT NULL REFERENCES assets(hash)  ON DELETE RESTRICT ON UPDATE RESTRICT,
       offline_limit        INTEGER       NOT NULL,
       notional_limit       INTEGER       NOT NULL,
       velocity_limit       INTEGER       NOT NULL,
       authorizer_uri       TEXT          NOT NULL REFERENCES personas(uri) ON DELETE RESTRICT ON UPDATE RESTRICT,
       authorizer_sig       TEXT          NOT NULL,
	authorizer_sig_timestamp	INTEGER	DEFAULT NULL,
       version              TEXT          NOT NULL,

       CONSTRAINT uc_holder_asset_authorizer_sig UNIQUE (holder_uri, asset_hash, authorizer_sig)
       );









CREATE TABLE IF NOT EXISTS transactions (
	id			INTEGER	PRIMARY KEY AUTOINCREMENT,
	-- asset_id		TEXT		DEFAULT NULL,
	asset			INTEGER	DEFAULT NULL REFERENCES assets(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
	reason			TEXT		DEFAULT NULL,
	sender			TEXT		NOT NULL,
	receiver		TEXT		NOT NULL REFERENCES personas(uri) ON DELETE RESTRICT ON UPDATE RESTRICT,
	amount			INTEGER	NOT NULL CHECK(amount>0),
	transaction_time	INTEGER	NOT NULL,
	transaction_hash	TEXT	       NOT NULL,
       acceptance_state     INTEGER       DEFAULT 0 CHECK(acceptance_state=-1 OR acceptance_state=0 OR acceptance_state=1),
	version		TEXT,

	CONSTRAINT uc_hash UNIQUE (transaction_hash)
	);

















	CREATE TABLE IF NOT EXISTS spends (
	id			INTEGER	PRIMARY KEY AUTOINCREMENT,
	output			INTEGER	NOT NULL DEFAULT -1,
	transaction_hash	TEXT		NOT NULL,
	transaction_id	INTEGER	REFERENCES transactions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,

	CONSTRAINT uc_double_spend UNIQUE (output)
	);









	CREATE TABLE IF NOT EXISTS outputs (
	id			INTEGER	PRIMARY KEY AUTOINCREMENT,
	vout		       INTEGER	NOT NULL CHECK(vout >= 0),
	amount		       INTEGER	NOT NULL CHECK(amount > 0),
	transaction_id	INTEGER	NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT ON UPDATE RESTRICT
	);









	CREATE TABLE IF NOT EXISTS attestations (
	id			INTEGER	PRIMARY KEY AUTOINCREMENT,
	transaction_id	INTEGER	NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,

	authorizer_sig	TEXT		DEFAULT NULL,
	authorizer_sig_time	INTEGER	DEFAULT NULL,
	authorizer_sig_valid	INTEGER	NOT NULL DEFAULT 0 CHECK(authorizer_sig_valid = 0 OR authorizer_sig_valid = 1),
	authorizer_approved	INTEGER	NOT NULL DEFAULT 0 CHECK(authorizer_approved = 0 OR authorizer_approved = 1),

	sender_sig		TEXT		NOT NULL,
	sender_sig_time	INTEGER	NOT NULL,
	sender_sig_valid	INTEGER	NOT NULL DEFAULT 0 CHECK(sender_sig_valid = 0 OR sender_sig_valid = 1),
	sender_approved	INTEGER	NOT NULL DEFAULT 0 CHECK(sender_approved = 0 OR sender_approved = 1),

	receiver_sig		TEXT		DEFAULT NULL,
	receiver_sig_time	INTEGER	DEFAULT NULL,
	receiver_sig_valid	INTEGER	NOT NULL DEFAULT 0 CHECK(receiver_sig_valid = 0 OR receiver_sig_valid = 1),
	receiver_approved	INTEGER	NOT NULL DEFAULT 0 CHECK(receiver_approved = 0 OR receiver_approved = 1)
	);

	--CREATE TABLE IF NOT EXISTS attestations (
	--id					INTEGER		PRIMARY KEY AUTOINCREMENT,
	--transaction_id		INTEGER		NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,

	--issuer_sig			TEXT		DEFAULT NULL,
	--issuer_sig_time		REAL		DEFAULT NULL,
	--issuer_sig_valid	INTEGER		DEFAULT NULL CHECK(issuer_sig_valid IS NULL or issuer_sig_valid = 0 OR issuer_sig_valid = 1),

	--sender_sig			TEXT		NOT NULL,
	--sender_sig_time		REAL		NOT NULL,
	--sender_sig_valid	INTEGER		NOT NULL DEFAULT 0 CHECK(sender_sig_valid = 0 OR sender_sig_valid = 1),

	--receiver_sig		TEXT		DEFAULT NULL,
	--receiver_sig_time	REAL		DEFAULT NULL,
	--receiver_sig_valid	INTEGER		DEFAULT NULL CHECK(receiver_sig_valid IS NULL or receiver_sig_valid = 0 OR receiver_sig_valid = 1),

	--CONSTRAINT uc_sigs UNIQUE (issuer_sig, issuer_sig_time, sender_sig, sender_sig_time, receiver_sig, receiver_sig_time)
	--);


	







	--CREATE TABLE IF NOT EXISTS encrypted_data (
	--id					INTEGER		PRIMARY KEY AUTOINCREMENT,
	--body				TEXT		NOT NULL,
	--receive_time		REAL		NOT NULL,
	--cleartext_encoding	INTEGER		NOT NULL
	--);







	--CREATE TABLE IF NOT EXISTS encrypted_symmetric_keys (
	--id					INTEGER		PRIMARY KEY AUTOINCREMENT,
	--encrypted_data_id	INTEGER		NOT NULL REFERENCES encrypted_data(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
	--symmetric_key		TEXT		NOT NULL,
	--public_key_id		TEXT		NOT NULL,
	--public_key_id_type	INTEGER		NOT NULL,
	--encrypt_time		REAL		NOT NULL
	--);








--CREATE TABLE contacts (
--       id			INTEGER		PRIMARY KEY NOT NULL,
--       name  		TEXT		NOT NULL,
--       subname	     	TEXT		NOT NULL DEFAULT "",
--       persona		INTEGER		REFERENCES personas(id),

--       encr_root_id		INTEGER		REFERENCES keychains(id),
--       encr_tip_id		INTEGER		REFERENCES keychains(id),
--       encr_is_final		INTEGER		NOT NULL DEFAULT 0,

--       sign_root_id		INTEGER		REFERENCES keychains(id),
--       sign_tip_id		INTEGER		REFERENCES keychains(id),
--       sign_is_final		INTEGER		NOT NULL DEFAULT 0,

       --root_txid		INTEGER		NOT NULL,
       --root_link_vout	INTEGER		NOT NULL,
--       create_time	TEXT		NOT NULL DEFAULT(STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW'))

       --FOREIGN KEY (root_txid, root_link_vout)
       --	       REFERENCES keychains (txid, link_vout)
	--       ON DELETE CASCADE ON UPDATE CASCADE
--       );



