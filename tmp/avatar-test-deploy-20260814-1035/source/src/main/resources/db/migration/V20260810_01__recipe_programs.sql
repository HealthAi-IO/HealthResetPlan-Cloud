CREATE TABLE IF NOT EXISTS recipe_program (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    category VARCHAR(32) NOT NULL,
    tags_json JSON NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    cooking_minutes INT NOT NULL DEFAULT 30,
    budget_per_day DECIMAL(10, 2) NULL,
    source_name VARCHAR(160) NOT NULL,
    source_url VARCHAR(500) NOT NULL,
    source_license VARCHAR(80) NOT NULL DEFAULT '',
    days_json JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    INDEX idx_recipe_program_status_sort (status, sort_order, id),
    INDEX idx_recipe_program_category (category, status)
);

SET @balanced_days = JSON_ARRAY(
  JSON_OBJECT('dayIndex',1,'weekDay','周一','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','燕麦鸡蛋早餐','ingredients',JSON_ARRAY('燕麦40g','鸡蛋50g','牛奶250ml','苹果150g'),'calories',480),'lunch',JSON_OBJECT('name','糙米鸡胸时蔬餐','ingredients',JSON_ARRAY('糙米70g','鸡胸肉120g','西兰花150g','胡萝卜80g'),'calories',620),'dinner',JSON_OBJECT('name','清蒸鱼杂粮餐','ingredients',JSON_ARRAY('鲈鱼120g','玉米100g','菠菜200g'),'calories',520))),
  JSON_OBJECT('dayIndex',2,'weekDay','周二','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','小米粥豆制品早餐','ingredients',JSON_ARRAY('小米50g','北豆腐80g','橙子150g'),'calories',430),'lunch',JSON_OBJECT('name','荞麦牛肉蔬菜餐','ingredients',JSON_ARRAY('荞麦面80g','瘦牛肉100g','青菜200g'),'calories',650),'dinner',JSON_OBJECT('name','虾仁豆腐杂粮饭','ingredients',JSON_ARRAY('虾仁100g','豆腐100g','杂粮饭80g','菌菇150g'),'calories',540))),
  JSON_OBJECT('dayIndex',3,'weekDay','周三','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','全麦蛋奶早餐','ingredients',JSON_ARRAY('全麦面包70g','鸡蛋50g','无糖酸奶150g'),'calories',450),'lunch',JSON_OBJECT('name','杂粮饭番茄鱼餐','ingredients',JSON_ARRAY('杂粮米80g','鱼肉120g','番茄150g','油菜150g'),'calories',610),'dinner',JSON_OBJECT('name','鸡丝蔬菜汤面','ingredients',JSON_ARRAY('全麦面70g','鸡胸肉80g','白菜200g'),'calories',500))),
  JSON_OBJECT('dayIndex',4,'weekDay','周四','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','红薯豆浆早餐','ingredients',JSON_ARRAY('红薯180g','无糖豆浆300ml','鸡蛋50g'),'calories',440),'lunch',JSON_OBJECT('name','米饭豆腐瘦肉餐','ingredients',JSON_ARRAY('大米75g','瘦猪肉80g','豆腐80g','彩椒180g'),'calories',640),'dinner',JSON_OBJECT('name','南瓜鸡肉蔬菜餐','ingredients',JSON_ARRAY('南瓜200g','鸡腿去皮100g','生菜200g'),'calories',510))),
  JSON_OBJECT('dayIndex',5,'weekDay','周五','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','玉米蛋奶早餐','ingredients',JSON_ARRAY('玉米150g','鸡蛋50g','牛奶250ml'),'calories',430),'lunch',JSON_OBJECT('name','糙米虾仁双蔬餐','ingredients',JSON_ARRAY('糙米75g','虾仁120g','西葫芦150g','木耳50g'),'calories',600),'dinner',JSON_OBJECT('name','豆腐鱼汤杂粮餐','ingredients',JSON_ARRAY('鱼肉100g','豆腐100g','杂粮饭60g','青菜200g'),'calories',520))),
  JSON_OBJECT('dayIndex',6,'weekDay','周六','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','杂粮粥坚果早餐','ingredients',JSON_ARRAY('杂粮50g','坚果10g','酸奶150g','梨150g'),'calories',460),'lunch',JSON_OBJECT('name','土豆牛肉蔬菜餐','ingredients',JSON_ARRAY('土豆180g','瘦牛肉100g','芹菜180g'),'calories',630),'dinner',JSON_OBJECT('name','荞麦鸡丝拌菜','ingredients',JSON_ARRAY('荞麦面70g','鸡胸肉90g','黄瓜100g','番茄100g'),'calories',510))),
  JSON_OBJECT('dayIndex',7,'weekDay','周日','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','山药蛋奶早餐','ingredients',JSON_ARRAY('山药180g','鸡蛋50g','牛奶250ml'),'calories',440),'lunch',JSON_OBJECT('name','杂粮米蒸鱼餐','ingredients',JSON_ARRAY('杂粮米80g','鱼肉130g','时令蔬菜250g'),'calories',620),'dinner',JSON_OBJECT('name','菌菇豆腐蔬菜餐','ingredients',JSON_ARRAY('豆腐120g','菌菇150g','青菜200g','小米40g'),'calories',500)))
);

INSERT INTO recipe_program (title, summary, category, tags_json, difficulty, cooking_minutes, budget_per_day, source_name, source_url, source_license, days_json, status, sort_order)
VALUES ('平衡膳食 7 天入门', '覆盖谷薯、蔬果、蛋奶、豆类和适量鱼禽肉，适合建立规律三餐。', '热门', JSON_ARRAY('食物多样','清淡烹调','新手友好'), '容易', 30, 38,
        '国家卫生健康委：合理膳食健康教育核心信息', 'https://www.nhc.gov.cn/xcs/cbcl/201706/d7d8ff3889be445b8bfb86a5efea952f.shtml', '政府公开资料，据原则重新编排', @balanced_days, 'published', 100);

SET @dash_days = JSON_ARRAY(
  JSON_OBJECT('dayIndex',1,'weekDay','周一','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','燕麦莓果低盐早餐','ingredients',JSON_ARRAY('燕麦45g','牛奶250ml','莓果100g'),'calories',450),'lunch',JSON_OBJECT('name','糙米烤鸡蔬菜餐','ingredients',JSON_ARRAY('糙米75g','鸡胸肉120g','西兰花200g'),'calories',610),'dinner',JSON_OBJECT('name','清蒸鱼豆类餐','ingredients',JSON_ARRAY('鱼肉120g','鹰嘴豆80g','绿叶菜200g'),'calories',530))),
  JSON_OBJECT('dayIndex',2,'weekDay','周二','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','全麦香蕉酸奶早餐','ingredients',JSON_ARRAY('全麦面包70g','香蕉100g','无糖酸奶150g'),'calories',440),'lunch',JSON_OBJECT('name','藜麦豆腐彩蔬餐','ingredients',JSON_ARRAY('藜麦70g','豆腐120g','彩椒200g'),'calories',590),'dinner',JSON_OBJECT('name','番茄鸡肉杂粮餐','ingredients',JSON_ARRAY('鸡胸肉100g','番茄180g','杂粮饭70g'),'calories',520))),
  JSON_OBJECT('dayIndex',3,'weekDay','周三','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','小米蛋奶早餐','ingredients',JSON_ARRAY('小米45g','鸡蛋50g','牛奶250ml'),'calories',430),'lunch',JSON_OBJECT('name','荞麦牛肉芹菜餐','ingredients',JSON_ARRAY('荞麦面75g','瘦牛肉90g','芹菜200g'),'calories',620),'dinner',JSON_OBJECT('name','豆腐菌菇青菜汤','ingredients',JSON_ARRAY('豆腐130g','菌菇150g','青菜200g','玉米100g'),'calories',500))),
  JSON_OBJECT('dayIndex',4,'weekDay','周四','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','红薯坚果豆浆早餐','ingredients',JSON_ARRAY('红薯160g','坚果10g','无糖豆浆300ml'),'calories',450),'lunch',JSON_OBJECT('name','糙米虾仁双蔬餐','ingredients',JSON_ARRAY('糙米75g','虾仁120g','西葫芦150g','胡萝卜80g'),'calories',600),'dinner',JSON_OBJECT('name','清炖鸡肉南瓜餐','ingredients',JSON_ARRAY('去皮鸡肉100g','南瓜200g','油菜180g'),'calories',510))),
  JSON_OBJECT('dayIndex',5,'weekDay','周五','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','燕麦苹果酸奶早餐','ingredients',JSON_ARRAY('燕麦40g','苹果150g','无糖酸奶150g'),'calories',420),'lunch',JSON_OBJECT('name','杂粮饭蒸鱼蔬菜餐','ingredients',JSON_ARRAY('杂粮米80g','鱼肉130g','时令蔬菜250g'),'calories',620),'dinner',JSON_OBJECT('name','扁豆番茄全麦面','ingredients',JSON_ARRAY('全麦面70g','扁豆80g','番茄180g'),'calories',520))),
  JSON_OBJECT('dayIndex',6,'weekDay','周六','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','玉米蛋奶早餐','ingredients',JSON_ARRAY('玉米150g','鸡蛋50g','牛奶250ml'),'calories',430),'lunch',JSON_OBJECT('name','土豆牛肉青菜餐','ingredients',JSON_ARRAY('土豆170g','瘦牛肉90g','绿叶菜200g'),'calories',610),'dinner',JSON_OBJECT('name','豆腐鱼汤小米餐','ingredients',JSON_ARRAY('豆腐100g','鱼肉90g','小米45g','白菜180g'),'calories',510))),
  JSON_OBJECT('dayIndex',7,'weekDay','周日','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','全麦花生水果早餐','ingredients',JSON_ARRAY('全麦面包70g','无盐花生酱10g','橙子150g','牛奶250ml'),'calories',470),'lunch',JSON_OBJECT('name','糙米鸡肉豆类餐','ingredients',JSON_ARRAY('糙米75g','鸡胸肉100g','芸豆80g','青菜180g'),'calories',630),'dinner',JSON_OBJECT('name','荞麦豆腐蔬菜餐','ingredients',JSON_ARRAY('荞麦面70g','豆腐120g','蔬菜220g'),'calories',500)))
);

INSERT INTO recipe_program (title, summary, category, tags_json, difficulty, cooking_minutes, budget_per_day, source_name, source_url, source_license, days_json, status, sort_order)
VALUES ('DASH 控压 7 天菜单', '以蔬果、全谷、低脂奶和豆类为主，减少高钠加工食品；热量需按个人需求调整。', '控压', JSON_ARRAY('低盐','全谷物','DASH'), '容易', 30, 42,
        '美国国立心肺血液研究所：A Week With the DASH Eating Plan', 'https://www.nhlbi.nih.gov/resources/week-dash-eating-plan', '美国政府健康教育资料，据原则本地化编排', @dash_days, 'published', 95);

SET @glucose_days = JSON_ARRAY(
  JSON_OBJECT('dayIndex',1,'weekDay','周一','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','燕麦鸡蛋控糖早餐','ingredients',JSON_ARRAY('燕麦35g','鸡蛋50g','牛奶200ml'),'calories',400),'lunch',JSON_OBJECT('name','杂粮饭鸡肉蔬菜餐','ingredients',JSON_ARRAY('杂粮米65g','鸡胸肉120g','非淀粉蔬菜250g'),'calories',570),'dinner',JSON_OBJECT('name','豆腐鱼肉蔬菜餐','ingredients',JSON_ARRAY('豆腐100g','鱼肉100g','青菜250g','玉米70g'),'calories',500))),
  JSON_OBJECT('dayIndex',2,'weekDay','周二','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','全麦豆浆早餐','ingredients',JSON_ARRAY('全麦面包60g','无糖豆浆300ml','番茄100g'),'calories',390),'lunch',JSON_OBJECT('name','荞麦牛肉双蔬餐','ingredients',JSON_ARRAY('荞麦面65g','瘦牛肉100g','蔬菜250g'),'calories',580),'dinner',JSON_OBJECT('name','南瓜虾仁豆腐餐','ingredients',JSON_ARRAY('南瓜120g','虾仁100g','豆腐80g','青菜200g'),'calories',480))),
  JSON_OBJECT('dayIndex',3,'weekDay','周三','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','小米蛋奶早餐','ingredients',JSON_ARRAY('小米40g','鸡蛋50g','牛奶200ml'),'calories',400),'lunch',JSON_OBJECT('name','糙米蒸鱼蔬菜餐','ingredients',JSON_ARRAY('糙米65g','鱼肉120g','蔬菜250g'),'calories',570),'dinner',JSON_OBJECT('name','鸡丝菌菇汤面','ingredients',JSON_ARRAY('全麦面60g','鸡胸肉80g','菌菇150g','青菜150g'),'calories',490))),
  JSON_OBJECT('dayIndex',4,'weekDay','周四','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','山药鸡蛋豆浆早餐','ingredients',JSON_ARRAY('山药120g','鸡蛋50g','无糖豆浆250ml'),'calories',390),'lunch',JSON_OBJECT('name','杂粮饭豆腐瘦肉餐','ingredients',JSON_ARRAY('杂粮米65g','瘦肉80g','豆腐80g','蔬菜220g'),'calories',590),'dinner',JSON_OBJECT('name','清蒸鱼西兰花餐','ingredients',JSON_ARRAY('鱼肉120g','西兰花200g','玉米70g'),'calories',470))),
  JSON_OBJECT('dayIndex',5,'weekDay','周五','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','燕麦酸奶坚果早餐','ingredients',JSON_ARRAY('燕麦35g','无糖酸奶150g','坚果8g'),'calories',410),'lunch',JSON_OBJECT('name','藜麦鸡肉彩蔬餐','ingredients',JSON_ARRAY('藜麦60g','鸡胸肉120g','彩椒220g'),'calories',580),'dinner',JSON_OBJECT('name','豆腐虾仁白菜餐','ingredients',JSON_ARRAY('豆腐110g','虾仁90g','白菜250g','红薯80g'),'calories',490))),
  JSON_OBJECT('dayIndex',6,'weekDay','周六','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','玉米蛋奶早餐','ingredients',JSON_ARRAY('玉米100g','鸡蛋50g','牛奶200ml'),'calories',390),'lunch',JSON_OBJECT('name','荞麦鱼肉蔬菜餐','ingredients',JSON_ARRAY('荞麦面65g','鱼肉110g','蔬菜250g'),'calories',570),'dinner',JSON_OBJECT('name','鸡肉豆类蔬菜汤','ingredients',JSON_ARRAY('鸡胸肉90g','鹰嘴豆60g','蔬菜250g'),'calories',500))),
  JSON_OBJECT('dayIndex',7,'weekDay','周日','meals',JSON_OBJECT('breakfast',JSON_OBJECT('name','全麦鸡蛋蔬菜早餐','ingredients',JSON_ARRAY('全麦面包60g','鸡蛋50g','黄瓜100g'),'calories',380),'lunch',JSON_OBJECT('name','糙米牛肉芹菜餐','ingredients',JSON_ARRAY('糙米65g','瘦牛肉90g','芹菜220g'),'calories',590),'dinner',JSON_OBJECT('name','菌菇豆腐杂粮餐','ingredients',JSON_ARRAY('豆腐120g','菌菇160g','杂粮米45g','青菜180g'),'calories',480)))
);

INSERT INTO recipe_program (title, summary, category, tags_json, difficulty, cooking_minutes, budget_per_day, source_name, source_url, source_license, days_json, status, sort_order)
VALUES ('控糖稳餐 7 天菜单', '固定三餐节奏，主食粗细搭配并搭配蛋白质和非淀粉蔬菜；用药者需遵医嘱调整。', '控糖', JSON_ARRAY('规律三餐','粗细搭配','食材克数'), '容易', 28, 40,
        '国家卫生健康委：成人糖尿病食养指南（2023年版）', 'https://www.nhc.gov.cn/sps/c100088/202301/f01895a06c5349ef999f25da833c166d.shtml', '政府公开指南，据原则重新编排', @glucose_days, 'published', 90);

INSERT INTO recipe_program (title, summary, category, tags_json, difficulty, cooking_minutes, budget_per_day, source_name, source_url, source_license, days_json, status, sort_order)
VALUES ('通勤快手 7 天菜单', '沿用平衡膳食组合，优先可提前准备和 30 分钟内完成的餐食。', '通勤', JSON_ARRAY('30分钟内','可备餐','少工具'), '容易', 25, 36,
        'USDA MyPlate：Plan Your Weekly Meals', 'https://www.myplate.gov/eathealthy/budget/budget-weekly-meals', '美国政府健康教育资料，据原则本地化编排', @balanced_days, 'published', 80),
       ('预算友好 7 天菜单', '使用常见谷物、蛋、豆制品和时令蔬菜，先盘点家中食材再安排一周。', '简单食材', JSON_ARRAY('常见食材','预算友好','少浪费'), '容易', 30, 28,
        'USDA MyPlate：Meeting Your MyPlate Goals on a Budget', 'https://www.myplate.gov/sites/default/files/cookbooks/MeetingYourMyPlateGoalsOnABudget_0.pdf', '美国政府健康教育资料，据原则本地化编排', @balanced_days, 'published', 75);

INSERT INTO recipe_program (title, summary, category, tags_json, difficulty, cooking_minutes, budget_per_day, source_name, source_url, source_license, days_json, status, sort_order)
VALUES ('轻负担减脂 7 天菜单', '用规律三餐、足量蔬菜和适量优质蛋白替代极端节食；具体能量应结合个人档案调整。', '减脂', JSON_ARRAY('规律三餐','足量蔬菜','拒绝极端节食'), '容易', 30, 38,
        '国家卫生健康委：合理膳食健康教育核心信息', 'https://www.nhc.gov.cn/xcs/cbcl/201706/d7d8ff3889be445b8bfb86a5efea952f.shtml', '政府公开资料，据原则重新编排', @glucose_days, 'published', 88),
       ('增肌蛋白分配 7 天菜单', '三餐分配蛋、奶、豆、鱼禽肉等蛋白来源，需配合个人能力范围内的抗阻运动。', '增肌', JSON_ARRAY('蛋白分配','蛋奶豆鱼禽','配合抗阻运动'), '容易', 32, 45,
        '国家卫生健康委：成人肌少症食养指南（2026年版）', 'https://www.nhc.gov.cn/sps/c100087/202604/a69d2fce21e040dc96cbc40b923a06d3/files/%E9%99%84%E4%BB%B65.%E6%88%90%E4%BA%BA%E8%82%8C%E5%B0%91%E7%97%87%E9%A3%9F%E5%85%BB%E6%8C%87%E5%8D%97%EF%BC%882026%E5%B9%B4%E7%89%88%EF%BC%89.pdf', '政府公开指南，据原则重新编排', @balanced_days, 'published', 85);
