/* Type-tris: type to unlock piece, then move (L/R) + rotate (↑), lock with Space or on land; line clears; top-out = game over */
(function () {
  "use strict";

  var COLS = 10;
  var ROWS = 16;
  /** Full rows cleared within one “level”; then HUD level +1 and fall speed +0.15 rows/s per level */
  var LINES_PER_LEVEL = 5;

  var WORDS =
    "ABLE ACHE ACID AGED ALSO AREA ARMY AWAY BABY BACK BAIR BALL BAND BANK BASE BATH BEAR BEAT BEEN BEND BEST BIKE BILL BIRD BLOW BLUE BOAT BODY BOOK BORN BOTH BOWL BURN BUSY CALL CALM CAME CAMP CARD CARE CASE CASH CAST CATS CAKE CAVE CHAP CHAT CHIP CITY CLAN CLAY CLIP CLOD CLUB COAL COAT CODE COIN COLD COME COOK COOL COPY CORN COST CREW CROP CROW CUBE CULT DARE DARK DATA DATE DAWN DAYS DEAD DEAL DEAR DEBT DECK DEEP DENY DESK DIAL DIET DISK DOCK DOES DONE DOOR DOSE DOWN DRAW DREW DROP DRUM DUCK DUCT DUNE DUSK DUTY EACH EARN EASE EAST EASY EDGE EDIT EPIC EVEN EVER EVIL EXAM EXIT FACE FACT FAIL FAIR FAKE FALL FAME FANG FARM FAST FATE FEED FEEL FEET FELL FELT FILE FILL FILM FIND FINE FIRE FIRM FISH FIST FIVE FLAG FLAT FLAW FLEW FLIP FLOW FOAM FOGY FOLD FOLK FOOD FOOT FORD FORK FORM FORT FOUL FOUR FREE FROG FROM FUEL FULL FUND FURY FUSE GAFF GAIN GAME GAVE GEAR GIFT GIRL GIVE GLAD GOAL GOAT GOLD GOLF GONE GOOD GRAB GRAY GREW GRID GRIM GRIN GROW GULF HAIL HAIR HALF HALL HAND HANG HARD HARM HATE HAVE HEAD HEAL HEAR HEAT HELD HELL HELP HERE HERO HIDE HIGH HIKE HILL HIRE HOLD HOLE HOLY HOME HOOK HOPE HORN HOSE HOST HOUR HUGE HUNG HUNT HURT IDEA IDLE INCH IRON ITEM JACK JOIN JOKE JUMP JUST KEEP KEPT KICK KILL KIND KING KISS KITE KNEE KNEW KNOW LACK LADY LAKE LAMB LAND LAST LATE LEAD LEAF LEAN LEAP LEFT LEND LENS LESS LIAR LIFT LIKE LINE LINK LION LIST LIVE LOAD LOAN LOCK LOGO LONE LONG LOOK LOOP LORD LOSE LOUD LOVE LUCK LUMP MADE MAIL MAIN MAKE MALE MANY MARK MARS MASK MASS MATE MATH MEAL MEAN MEAT MEET MENU MESH MILD MILE MILK MIND MINE MINT MISS MODE MOOD MOON MORE MOST MOSS MOVE MUCH MUST MYTH NAME NEAR NECK NEED NEWS NEXT NICE NINE NODE NOON NOTE NOUN OKAY ONLY ONTO OPEN ORAL OVER OWLS PACE PACK PAGE PAID PAIN PAIR PALM PARK PART PASS PAST PATH PEAK PEAR PICK PILE PINE PINK PIPE PLAN PLAY PLOT PLUG PLUS POEM POET POLE POLL POND PONY POOL POOR PORT POST PULL PURE PUSH RACE RACK RAID RAIL RAIN RANK RARE RATE READ REAL RECK REED REEF REEK REEL REIN RELY RENT REST RICE RICH RIDE RIFT RING RISE RISK ROAD ROAR ROCK RODE ROLE ROLL ROOF ROOM ROOT ROPE ROSE ROSY ROUT RUDE RUGS RULE RUSH RUST SAFE SAGE SAID SAIL SAKE SALT SAME SAND SANK SAVE SCAN SCAR SEAL SEAT SEED SEEK SEEM SEEN SELF SELL SEND SENT SETT SHED SHIP SHOE SHOP SHOT SHOW SHUT SICK SIDE SIGN SILK SING SINK SITE SIZE SKIN SKIP SLAB SLAG SLAM SLAP SLAW SLAY SLEW SLIM SLIP SLOG SLOT SLOW SNAP SNOW SOFT SOLO SOME SONG SOON SORT SOUL SOUP SOUR SPAN SPAR SPAT SPAY SPEC SPED SPIN SPIT SPOT STAR STAY STEP STIR STOP STOW STUB STUD SUCH SUIT SUNK SUNG SURE SWAB SWAP SWIM SYNC TACT TAIL TAKE TALE TALK TALL TANK TASK TEAM TEAR TELL TEND TENT TERM TEST TEXT THAN THAT THEM THEN THIN THIS THOU TIDE TILE TILT TIME TINY TIRE TOED TOFU TOLD TOLL TOMB TONE TOOK TOOL TOPS TORN TOTE TRAP TREE TREK TRIM TRIO TRIP TROD TRUE TUBE TUNA TUNE TURN TUSK TWIN TYPE UNDO UNIT UPON URGE USED USER VAIN VALE VARY VAST VEIL VEIN VENT VERY VEST VICE VIEW VINE VISA VOID VOLT VOTE WADE WAGE WAIT WAKE WALK WALL WANT WARD WARM WASH WAVE WAYS WEAK WEAR WEEK WELL WEST WHAT WHEW WHEN WHIP WHOM WIDE WIFE WILD WILL WIND WINE WIRE WISE WISH WITH WOLF WOOD WORD WORE WORK WORM WORN WRAP WRIT YANK YARD YARN YAWN YEAR YELL YOGA YOKE YORK YOUR YOWL ZERO ZEST ZINC ZONE ZOOM NEON STACK BLOCKS SPEED SPLIT TETRIS VECTOR" +
    " ABOUT ABOVE ADAPT ADMIT ADULT AFTER AGAIN AGENT AGREE AHEAD ALARM ALBUM ALERT ALIEN ALIKE ALIVE ALLOW ALONE ALONG ALTER AMBER ANGEL ANGER ANGLE ANGRY APART APPLE APPLY ARENA ARGUE ARISE ARROW ASIDE ASSET AUDIO AUDIT AVERT AWARD AWARE BADLY BASIC BASIS BEACH BEGAN BEGIN BEING BELOW BENCH BILLY BIRTH BLACK BLADE BLANK BLAST BLAZE BLEAK BLEND BLESS BLIND BLOCK BLOOD BLOOM BLOWN BOARD BOAST BOOST BOOTH BRAKE BRAND BRAVE BREAD BREAK BREED BRIEF BRING BROAD BROKEN BROWN BRUSH BUILD BUILT BUNCH BURST BUYER CABLE CALIF CANDY CARRY CARVE CAUSE CHAIN CHAIR CHAOS CHARM CHART CHASE CHEAP CHECK CHEST CHIEF CHILD CHINA CHOSE CHUNK CIVIC CIVIL CLAIM CLASS CLEAN CLEAR CLIMB CLOCK CLOSE COACH COAST COULD COUNT COURT COVER CRAFT CRASH CREAM CRIME CROSS CROWD CROWN CURVE CYCLE DAILY DANCE DATED DEALT DEATH DEBUT DELAY DEPTH DOING DOUBT DRAFT DRAMA DREAM DRESS DRINK DRIVE DROVE DROWN EAGER EARLY EARTH EIGHT ELITE EMPTY ENEMY ENJOY ENTER EPOXY EQUAL ERROR EVENT EVERY EXACT EXIST EXTRA FAITH FAULT FIBER FIELD FIFTY FINAL FIRST FLAME FLASH FLEET FLOOR FOCUS FORCE FORGE FORTH FORTY FOUND FRAME FRANK FROST FROZE FRUIT FULLY FUNNY GIANT GIVEN GLASS GLOBE GOING GRACE GRADE GRAND GRANT GRASS GREAT GREEN GROSS GROUP GROWN GUARD GUESS GUEST GUIDE HABIT HAPPY HARSH HEART HEAVY HEROE HORSE HOTEL HOUSE HUMAN HUMOR IDEAL IMAGE INDEX INNER INPUT ISSUE ITALY JAPAN JIMMY JOINT JONES JUDGE KNIFE KNOCK LABEL LARGE LASER LATER LAUGH LAYER LEARN LEAST LEAVE LEGAL LEVEL LIGHT LIMIT LINKS LIVER LOCAL LOOSE LORRY LOVER LUCKY LUNCH LYING MAGIC MAJOR MAKER MARCH MARRY MASON MATCH MAYBE MAYOR MEANT MEDIA METAL MIDST MIGHT MINOR MINUS MIXED MODEL MONEY MONTH MORAL MOTOR MOUNT MOUSE MOUTH MOVIE MUSIC NEEDS NEVER NEWLY NIGHT NOISE NORTH NOTED NOVEL NURSE OCCUR OCEAN OFFER OFTEN ORDER OTHER OUTER OWNER PANEL PAPER PARTY PATCH PAUSE PEACE PEDAL PENNY PHASE PHONE PHOTO PIECE PILOT PITCH PLACE PLAIN PLANE PLANT PLATE POINT POLAR PORCH POUND POWER PRESS PRICE PRIDE PRIME PRINT PRIOR PRIZE PROOF PROUD PROVE PUPPY QUART QUEEN QUICK QUIET QUITE RADIO RAISE RANGE RAPID RATIO REACH REACT READY REALM REBEL REFER RELAX RELAY RENAL RENEW REPLY RESET RESIN RETRO REVUE RHINO RIDER RIDGE RIGHT RILEY RIPEN RISEN RIVER ROBIN ROGER ROMAN ROUGH ROUND ROUTE ROYAL RULER RURAL SADLY SAINT SALAD SALON SAVED SCALE SCARE SCARF SCENE SCOPE SCORE SCOUT SCRAP SCREW SERVE SETUP SEVEN SHADE SHAKE SHAME SHAPE SHARE SHARK SHARP SHEET SHELF SHELL SHIFT SHINE SHIRT SHOCK SHOOT SHORT SHOWN SHRUB SHUCK SHUNT SIGHT SILLY SINCE SIXTY SKATE SKILL SKIRT SLATE SLEPT SLICE SLIDE SLIME SMALL SMART SMILE SNAKE SNEAK SOLAR SOLID SOLVE SORRY SOUND SOUTH SPACE SPARK SPEAK SPEED SPELL SPEND SPENT SPICE SPILL SPINE SPIRE SPIRY SPOIL SPOKE SPOON SPORT STAFF STAGE STAIN STAKE STALE STALL STAMP STAND STARE START STATE STEAK STEAL STEAM STEEL STICK STIFF STILL STING STOCK STONE STOOD STOOL STOOP STORE STORM STORY STRAP STRAW STRAY STRIP STUCK STUDY STUFF STYLE SUGAR SUITE SUPER SWAMP SWEAT SWEEP SWIFT SWING SWORD SYNOD TABLE TAKEN TASTE TAXES TEACH TEAMY TEMPO TENOR TENSE TERMS THANK THEME THERE THICK THING THINK THIRD THOSE THREE THREW THROW TIGHT TIMER TITAN TODAY TOKEN TONIC TOOTH TOPIC TOTAL TOUCH TOUGH TOWER TRACE TRACK TRADE TRAIN TREAT TREND TRIAD TRIAL TRIBE TRICK TROOP TRUST TRUTH TWICE TWIST ULTRA UNDER UNDUE UNION UNITY UNTIL UPPER URBAN USAGE USUAL VALID VALUE VAULT VENUE VIDEO VIRAL VISIT VITAL VOCAL VODKA VOGUE VOICE VOTER WAFER WAGON WAIST WASTE WATCH WATER WEARY WEAVE WEIGH WHALE WHEAT WHEEL WHERE WHICH WHILE WHINE WHIRL WHITE WHOLE WHOOP WHORL WIDTH WOMAN WOMEN WORLD WORRY WORSE WORST WORTH WOULD WOUND WRIST WRITE WRONG WROTE YACHT YEARN YIELD YOUNG YOUTH ZEBRA ZONAL" +
    " ACTION ACTIVE ADVICE ADVISE AFFORD AGENCY ALMOST AMOUNT ANIMAL ANSWER APPEAL ARRIVE ARTIST ASKING ATTACK ATTEND AWARDS BEACON BEHIND BELONG BETTER BRANCH BREATH BRIDGE BRIGHT BUDGET BUTTON CAMERA CANDLE CANNOT CARBON CAREER CASTLE CAUGHT CHANGE CHARGE CHOICE CHOSEN CIRCLE CITIES CLOSED CLOSER CLOUDS COFFEE COLUMN COMMON CORNER COURSE COUSIN CREATE CREDIT CRISIS CUSTOM DAMAGE DANCER DEBATE DECIDE DEGREE DESIGN DETAIL DEVICE DINNER DIRECT DIVIDE DOCTOR DOUBLE DRAGON DRIVEN EFFECT EFFORT ENGINE ENOUGH ENTIRE ETHICS EXCEPT EXPECT EXPERT FACING FACTOR FAMILY FAMOUS FARMER FATHER FIGURE FILTER FLIGHT FLOWER FOLLOW FOREST FORMAT FORMER FRIEND FUTURE GARDEN GATHER GLOBAL GOLDEN GRANTS GROUND GROWTH GUITAR HAMMER HANDLE HAPPEN HARDLY HEALTH HEIGHT HIDDEN HIGHLY HOLDER HONEST IMPACT IMPORT INCOME INDEED INSIDE INVEST ISLAND JACKET LEADER LEAGUE LENGTH LESSON LETTER LEVELS LIKELY LISTEN LITTLE LIVING LOCKED MAKING MANAGE MANNER MANUAL MARKER MARKET MASTER MATTER MEMBER MEMORY MENTAL METHOD MIDDLE MINUTE MIRROR MOBILE MODERN MOMENT MOTHER MOTION MOVING NARROW NEARLY NORMAL NOTICE NUMBER OBJECT OFFICE ONLINE OPTION ORANGE ORDERS PEOPLE PERIOD PERSON PLANET PLAYER PLEASE POCKET POLICE POLICY PUBLIC PURPLE RANDOM RATING READER REALLY REASON RECENT RECORD REDUCE REGION RELATE REMAIN REMOTE REMOVE REPAIR REPEAT REPORT RESULT RETURN REVEAL REVIEW REWARD RIDING RISING RUBBER SALARY SAMPLE SAVING SCHOOL SCREEN SEARCH SEASON SECOND SECRET SELECT SENIOR SERIES SERVER SHOULD SIGNAL SILENT SILVER SIMPLE SINGLE SISTER SLOWLY SOCIAL SOURCE SPEECH SPIRIT SPREAD SPRING SQUARE STABLE STATUS STREET STRIKE STRONG STUDIO SUMMER SUPPLY SYMBOL SYSTEM TACKLE TALENT TARGET TEMPLE THANKS THROAT TICKET TIMELY TRAVEL TREATY TRIPLE TRYING TUNNEL TWELVE UNIQUE UNLESS UPDATE VALLEY WEALTH WEIGHT WINDOW WINNER WITHIN WONDER WRITER YELLOW ABILITY ACCOUNT ACHIEVE ADDRESS ADVANCE AGAINST AIRLINE AIRPORT AMAZING ANALYZE ANCHORS ANXIETY ARRANGE ARRIVAL ARTICLE AVERAGE AWKWARD BALANCE BARRIER BATTERY BELIEVE BENEATH BILLION BROUGHT BUILDER BURNING CAPABLE CAPTAIN CAREFUL CENTRAL CHAMBER CHANNEL CHAPTER CHARITY CIRCUIT CLIMATE CLOSURE COLLECT COMPANY COMPLEX CONCEPT CONCERN CONDUCT CONNECT CONSULT CONTACT CONTENT CONTEST CONTEXT CONTROL COUNCIL COUNTRY CRUCIAL CRYSTAL CULTURE CURRENT DECISION DECLARE DEFAULT DELIVER DESKTOP DEVELOP DISPLAY DYNAMIC EASTERN ECONOMY EDITION ELECTED ELEMENT EMERGED EMOTION EMPLOYS ENGAGED ENHANCE ESSENCE EVENING EXACTLY EXAMINE EXAMPLE EXCITED EXHIBIT EXPENSE EXPLAIN EXPLORE EXPRESS EXTREME FACTORY FASHION FEATURE FICTION FITTING FOREIGN FOREVER FORWARD FREEDOM FREIGHT FRIENDS FURTHER GALLERY GENERAL GENETIC GENUINE GLIMPSE GRAVITY HAPPILY HEAVILY HORIZON ILLNESS IMAGINE IMPRESS IMPROVE INCLUDE INQUIRY INSIGHT INSTALL INTENSE INVOLVE ISOLATE JOURNEY JUSTICE LICENSE LIMITED LOCALLY LOGICAL MACHINE MANAGER MARRIED MENTION MISSION MISTAKE MIXTURE MONITOR MYSTERY NATURAL NEITHER NUCLEAR OFFICER OFFLINE OUTLOOK PACKAGE PAINTER PARTNER PASSAGE PATIENT PATTERN PAYMENT PENDING PERFORM PIONEER PLASTIC POPULAR PORTION PRECISION PREPARE PRESENT PRIMARY PRINTER PROBLEM PROCESS PRODUCT PROFILE PROGRAM PROMISE PROPOSE PROTECT PROVIDE PUBLISH PURPOSE QUALIFY QUARTER RADICAL READING REALITY RECEIPT RECOVER REFLECT REGULAR RELATED RELEASE REPLACE REQUEST REQUIRE RESERVE RESOLVE RESPECT RESPOND RESTORE REVENUE ROUTINES SCIENCE SECTION SECURED SENSORY SERIOUS SESSION SETTING SILENCE SIMILAR SINCERE SOCIETY SPECIAL SPECIES SPONSOR STATION STORAGE STRANGE SUBJECT SUGGEST SUMMARY SUPPORT SURFACE SURPLUS SURVIVE TEACHER TENDENCY TENSION TERRAIN THERAPY THOUGHT THROUGH THUNDER TONIGHT TOTALLY TRAFFIC TRAGEDY TRAINED TREATED TRIBUTE TROUBLE TYPICAL UNIFORM UNKNOWN UNUSUAL UTILITY VACUUM VALUABLE VARIOUS VERSION VILLAGE VIOLENT VIRTUAL WARNING WEATHER WEEKEND WELCOME WELFARE WESTERN WHETHER WISHING WORKING WRAPPED WRITING ABSOLUTE ACADEMIC ACCURATE ACTIVITY ADDITION ADVANCED ANALYSIS ANIMATED ANNOUNCE APPROVAL ARTISTIC ASSEMBLY ASSIGNMENT AUTOMATIC DISCOVERY EFFECTIVE ENCOURAGE EQUIPMENT ESTIMATE FINANCE GENERATE HOSPITAL IDENTIFY IDENTITY LABELING LOCATOR MAGNETIC MEDICAL MEDICINE MULTIPLE NATIONAL NORMALLY OFFICIALS OPERATOR PACKAGES PAINTING PHYSICS PLATFORMS POLITICS POSITION PRACTICE PRESENCE PROBABLY PROPERTY PROTEIN PROTESTS QUESTION REACTION READINGS RECEIVED REGIONAL RELATION REQUIRES RESEARCH RESPONSE SECURITY SERVICES SHOULDER SOMEWHAT STANDARD STRATEGY STRENGTH STUDENTS SURVIVAL TEACHING TERRITORY THINKING THOUSAND TRAINING TREATMENT TYPICALLY ULTIMATE VARIABLE VERSIONS VICTORIA VIOLENCE WEAKNESS";

  var WORD_LIST = WORDS.split(/\s+/).filter(function (w) {
    return w.length >= 3 && w.length <= 10 && /^[A-Z]+$/.test(w);
  });

  var BEST_KEY = "typetrisBestByDifficulty";

  function getSessionDifficulty() {
    var raw = sessionStorage.getItem("typetrisDifficulty");
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch (e) {
      return null;
    }
  }

  function difficultyParams(tier, subLevel) {
    var t = (tier || "easy").toLowerCase();
    var lv = Math.max(1, Math.min(3, Number(subLevel) || 1));
    var fallBase = { easy: 0.4, medium: 0.75, hard: 1.25 }[t] || 1;
    var fallRowsPerSec = fallBase * (0.9 + lv * 0.2);
    var lenMin = { easy: 3, medium: 5, hard: 6 }[t];
    var lenMax = { easy: 4, medium: 5, hard: 7 }[t];
    lenMin = Math.min(10, lenMin + (lv - 1));
    lenMax = Math.min(10, lenMax + (lv - 1));
    if (lenMax < lenMin) lenMax = lenMin;
    return { fallRowsPerSec: fallRowsPerSec, lenMin: lenMin, lenMax: lenMax };
  }

  function pickWord(lenMin, lenMax) {
    var pool = WORD_LIST.filter(function (w) {
      return w.length >= lenMin && w.length <= lenMax;
    });
    if (pool.length === 0) return "TYPE";
    return pool[(Math.random() * pool.length) | 0];
  }

  function bestScoreFor(tier, level) {
    var raw = sessionStorage.getItem(BEST_KEY);
    if (!raw) return 0;
    try {
      var map = JSON.parse(raw);
      return Number(map[tier + "-" + level]) || 0;
    } catch (e) {
      return 0;
    }
  }

  function persistBestIfNeeded(tier, level, score) {
    var map = {};
    try {
      map = JSON.parse(sessionStorage.getItem(BEST_KEY) || "{}");
    } catch (e) {
      map = {};
    }
    var k = tier + "-" + level;
    if ((map[k] || 0) < score) {
      map[k] = score;
      sessionStorage.setItem(BEST_KEY, JSON.stringify(map));
    }
  }

  function byId(id) {
    return document.getElementById(id);
  }

  function setText(id, value) {
    var el = byId(id);
    if (el) el.textContent = String(value);
  }

  // Classic tetromino-based generator.
  // - For word length <= 4: pick a random I/J/L/O/S/T/Z and carve a connected subset of size n.
  // - For word length > 4: start from a tetromino, then extend in a straight line from one side
  //   until it reaches the word length.
  function generateTetrominoCells(n) {
    if (n <= 0) return [];

    var TETROMINOES = [
      // I
      [
        { x: 0, y: 0 },
        { x: 1, y: 0 },
        { x: 2, y: 0 },
        { x: 3, y: 0 },
      ],
      // O
      [
        { x: 0, y: 0 },
        { x: 1, y: 0 },
        { x: 0, y: 1 },
        { x: 1, y: 1 },
      ],
      // T
      [
        { x: 1, y: 0 },
        { x: 0, y: 1 },
        { x: 1, y: 1 },
        { x: 2, y: 1 },
      ],
      // L
      [
        { x: 0, y: 0 },
        { x: 0, y: 1 },
        { x: 1, y: 1 },
        { x: 2, y: 1 },
      ],
      // J
      [
        { x: 2, y: 0 },
        { x: 0, y: 1 },
        { x: 1, y: 1 },
        { x: 2, y: 1 },
      ],
      // S
      [
        { x: 1, y: 0 },
        { x: 2, y: 0 },
        { x: 0, y: 1 },
        { x: 1, y: 1 },
      ],
      // Z
      [
        { x: 0, y: 0 },
        { x: 1, y: 0 },
        { x: 1, y: 1 },
        { x: 2, y: 1 },
      ],
    ];

    function normalize(cells) {
      var minX = cells[0].x;
      var minY = cells[0].y;
      for (var i = 1; i < cells.length; i++) {
        minX = Math.min(minX, cells[i].x);
        minY = Math.min(minY, cells[i].y);
      }
      return cells.map(function (c) {
        return { x: c.x - minX, y: c.y - minY };
      });
    }

    function key(x, y) {
      return x + "," + y;
    }

    // Pick a connected subset of size `n` from within a 4-cell tetromino.
    function carveConnectedSubset(tetCells, want) {
      if (want === 4) return tetCells;
      var tetSet = {};
      tetCells.forEach(function (c) {
        tetSet[key(c.x, c.y)] = c;
      });

      var dirs = [
        { x: 1, y: 0 },
        { x: -1, y: 0 },
        { x: 0, y: 1 },
        { x: 0, y: -1 },
      ];

      var attempts = 200;
      while (attempts-- > 0) {
        var start = tetCells[(Math.random() * tetCells.length) | 0];
        var subset = [start];
        var inSubset = {};
        inSubset[key(start.x, start.y)] = true;

        while (subset.length < want) {
          var frontier = [];
          subset.forEach(function (c) {
            for (var d = 0; d < dirs.length; d++) {
              var nx = c.x + dirs[d].x;
              var ny = c.y + dirs[d].y;
              if (tetSet[key(nx, ny)] && !inSubset[key(nx, ny)]) {
                frontier.push({ x: nx, y: ny });
              }
            }
          });

          if (frontier.length === 0) break;

          var pick = frontier[(Math.random() * frontier.length) | 0];
          inSubset[key(pick.x, pick.y)] = true;
          subset.push(pick);
        }

        if (subset.length === want) return subset;
      }

      // Fallback: just take first `want` cells (should be rare).
      return tetCells.slice(0, want);
    }

    // Pick base tetromino.
    var base = TETROMINOES[(Math.random() * TETROMINOES.length) | 0];
    // Clone so we can safely modify.
    base = base.map(function (c) {
      return { x: c.x, y: c.y };
    });

    if (n <= 4) {
      var subset = carveConnectedSubset(base, n);
      return normalize(subset);
    }

    // n > 4: extend in a straight line from one extreme side.
    var dirs = [
      { x: 1, y: 0 },
      { x: -1, y: 0 },
      { x: 0, y: 1 },
      { x: 0, y: -1 },
    ];
    var dir = dirs[(Math.random() * dirs.length) | 0];

    var extreme = -99999;
    for (var i = 0; i < base.length; i++) {
      var p = base[i].x * dir.x + base[i].y * dir.y;
      if (p > extreme) extreme = p;
    }

    var anchorCandidates = [];
    for (var j = 0; j < base.length; j++) {
      var pp = base[j].x * dir.x + base[j].y * dir.y;
      if (pp === extreme) anchorCandidates.push(base[j]);
    }
    var anchor = anchorCandidates[(Math.random() * anchorCandidates.length) | 0];

    var cells = base.slice();
    var tailLen = n - 4;
    for (var k = 1; k <= tailLen; k++) {
      cells.push({ x: anchor.x + dir.x * k, y: anchor.y + dir.y * k });
    }
    return normalize(cells);
  }

  function wordToParts(word) {
    var cells = generateTetrominoCells(word.length);
    // Make letter placement stable/readable by sorting cells top-to-bottom, then left-to-right.
    cells.sort(function (a, b) {
      if (a.y !== b.y) return a.y - b.y;
      return a.x - b.x;
    });

    var parts = [];
    for (var i = 0; i < word.length; i++) {
      parts.push({ x: cells[i].x, y: cells[i].y, ch: word.charAt(i), wi: i });
    }
    return parts;
  }

  function rotatePartsCw(parts) {
    var out = parts.map(function (p) {
      return { x: p.y, y: -p.x, ch: p.ch, wi: p.wi };
    });
    var minX = Math.min.apply(
      null,
      out.map(function (p) {
        return p.x;
      })
    );
    var minY = Math.min.apply(
      null,
      out.map(function (p) {
        return p.y;
      })
    );
    return out.map(function (p) {
      return { x: p.x - minX, y: p.y - minY, ch: p.ch, wi: p.wi };
    });
  }

  function partExtents(parts) {
    var maxX = 0;
    var maxY = 0;
    for (var i = 0; i < parts.length; i++) {
      maxX = Math.max(maxX, parts[i].x);
      maxY = Math.max(maxY, parts[i].y);
    }
    return { w: maxX + 1, h: maxY + 1 };
  }

  function setPausedUI(paused, overlay, input, pauseButton) {
    if (paused) {
      if (overlay) overlay.classList.remove("hidden");
      if (input) {
        input.disabled = true;
        input.blur();
      }
      if (pauseButton) pauseButton.setAttribute("aria-pressed", "true");
    } else {
      if (overlay) overlay.classList.add("hidden");
      if (input) {
        input.disabled = false;
        input.focus();
      }
      if (pauseButton) pauseButton.setAttribute("aria-pressed", "false");
    }
  }

  function wire() {
    var difficulty = getSessionDifficulty();
    var username = sessionStorage.getItem("typetrisPlayerUsername");
    if (!username || !difficulty) {
      window.location.href = "player-menu.html";
      return;
    }

    var tier = difficulty.tier || "easy";
    var subLevel = difficulty.level || 1;
    if (
      window.typetrisDifficultyProgress &&
      typeof window.typetrisDifficultyProgress.isUnlocked === "function" &&
      !window.typetrisDifficultyProgress.isUnlocked(tier, subLevel)
    ) {
      window.location.href = "difficulty.html";
      return;
    }
    var params = difficultyParams(tier, subLevel);
    var best = bestScoreFor(tier, subLevel);

    var board = byId("game-board");
    var playfieldWrap = byId("playfield-wrap");
    var playfield = byId("playfield");
    var lockedLayer = byId("locked-layer");
    var activeLayer = byId("active-layer");
    var input = byId("typing-input");
    var typingHint = byId("typing-hint");
    var phaseLabel = byId("hud-control-phase");
    var overlay = byId("pause-overlay");
    var pauseButton = byId("pause-button");
    var resumeButton = byId("pause-resume");
    var optionsButton = byId("pause-options");
    var saveButton = byId("pause-save");
    var quitButton = byId("pause-quit");

    var cellSize = 28;
    var grid = [];
    var i;
    var j;
    for (i = 0; i < ROWS; i++) {
      grid[i] = [];
      for (j = 0; j < COLS; j++) {
        grid[i][j] = null;
      }
    }

    var WPM_ROLLING_MS = 5000;

    var state = {
      paused: false,
      over: false,
      score: 0,
      linesCleared: 0,
      lineStreak: 0,
      mult: 1,
      correctStrokes: 0,
      wrongStrokes: 0,
      wpmCharTimes: [],

      // Tracks time spent in "typing-eligible" state (i.e., while the current piece is
      // NOT unlocked yet). This prevents counting time when the user is only moving/placing.
      typingClockMs: 0,
      typingClockLastWallMs: performance.now(),

      fallAccum: 0,
      piece: null
    };

    // -----------------------
    // Audio (Web Audio API)
    // -----------------------
    var audioCtx = null;
    var masterGain = null;
    var sfxGain = null;
    var bgmGain = null;
    var bgmRaf = null;
    var bgmPlaying = false;

    function ensureAudioStarted() {
      try {
        if (!audioCtx) {
          var Ctx = window.AudioContext || window.webkitAudioContext;
          if (!Ctx) return;
          audioCtx = new Ctx();
          masterGain = audioCtx.createGain();
          masterGain.gain.value = 0.9;

          sfxGain = audioCtx.createGain();
          sfxGain.gain.value = 1.0;
          bgmGain = audioCtx.createGain();
          bgmGain.gain.value = 0.28;

          // Route both buses to master.
          sfxGain.connect(masterGain);
          bgmGain.connect(masterGain);
          masterGain.connect(audioCtx.destination);
        }

        if (audioCtx.state === "suspended") {
          // Must be triggered by user gesture to be allowed in browsers.
          audioCtx.resume();
        }

        if (!bgmPlaying) {
          startBgm();
        }
      } catch (e) {
        // Ignore audio failures (e.g., autoplay restrictions).
      }
    }

    function playOsc(type, freq, time, durationSec, gainPeak) {
      if (!audioCtx) return;
      var osc = audioCtx.createOscillator();
      var gain = audioCtx.createGain();
      osc.type = type || "triangle";
      osc.frequency.setValueAtTime(freq, time);

      gain.gain.setValueAtTime(0.0001, time);
      gain.gain.exponentialRampToValueAtTime(gainPeak, time + 0.01);
      gain.gain.exponentialRampToValueAtTime(0.0001, time + durationSec);

      osc.connect(gain);
      gain.connect(sfxGain);
      osc.start(time);
      osc.stop(time + durationSec);
    }

    function setBgmPaused(paused) {
      if (!audioCtx || !bgmGain) return;
      var t = audioCtx.currentTime;
      bgmGain.gain.cancelScheduledValues(t);
      bgmGain.gain.setValueAtTime(bgmGain.gain.value, t);
      bgmGain.gain.linearRampToValueAtTime(paused ? 0.05 : 0.28, t + 0.05);
    }

    function startBgm() {
      if (!audioCtx || bgmPlaying) return;
      bgmPlaying = true;
      var bpm = 96;
      var beat = 60 / bpm;
      // Chord progressions (in frequencies) - mild and non-intrusive.
      var chords = [
        [261.63, 329.63, 392.0], // C
        [293.66, 369.99, 440.0], // D
        [329.63, 415.3, 493.88], // E
        [196.0, 246.94, 293.66], // G-ish
      ];
      var idx = 0;
      var nextTime = audioCtx.currentTime + 0.05;

      function schedule() {
        if (!bgmPlaying) return;
        var horizon = 0.25;
        while (nextTime < audioCtx.currentTime + horizon) {
          var chord = chords[idx % chords.length];
          // schedule chord as stacked triangle oscillators with a gentle filter envelope
          for (var i = 0; i < chord.length; i++) {
            var f = chord[i];
            var osc = audioCtx.createOscillator();
            var g = audioCtx.createGain();
            osc.type = "sine";
            osc.frequency.setValueAtTime(f, nextTime);
            g.gain.setValueAtTime(0.0001, nextTime);
            g.gain.exponentialRampToValueAtTime(0.12, nextTime + 0.03);
            g.gain.exponentialRampToValueAtTime(0.0001, nextTime + beat * 0.55);
            osc.connect(g);
            g.connect(bgmGain);
            osc.start(nextTime);
            osc.stop(nextTime + beat * 0.6);
          }

          nextTime += beat * 0.5;
          idx++;
        }
        bgmRaf = window.requestAnimationFrame(schedule);
      }

      bgmRaf = window.requestAnimationFrame(schedule);
    }

    function stopBgm() {
      if (!audioCtx) return;
      bgmPlaying = false;
      if (bgmRaf) window.cancelAnimationFrame(bgmRaf);
      bgmRaf = null;
      if (bgmGain) {
        var t = audioCtx.currentTime;
        bgmGain.gain.cancelScheduledValues(t);
        bgmGain.gain.setTargetAtTime(0.0001, t, 0.05);
      }
    }

    function playSfxBlockLand() {
      if (!audioCtx) return;
      var t = audioCtx.currentTime;
      playOsc("square", 140, t, 0.12, 0.22);
      // quick click
      playOsc("triangle", 320, t + 0.01, 0.06, 0.08);
    }

    function playSfxRowClear(clearedCount) {
      if (!audioCtx) return;
      var t = audioCtx.currentTime;
      var base = 260 + (clearedCount - 1) * 60;
      // Rising triple; slightly longer sustain so it "feels" like a clear.
      playOsc("triangle", base, t, 0.38, 0.22);
      playOsc("triangle", base * 1.25, t + 0.03, 0.30, 0.16);
      playOsc("triangle", base * 1.5, t + 0.06, 0.24, 0.14);
    }

    function playSfxTypingError() {
      if (!audioCtx) return;
      var t = audioCtx.currentTime;
      // Sharp high-pitched descending "error beep" (avoid block-land tones).
      // Primary: square wave from ~620Hz down to ~300Hz in ~70ms.
      if (audioCtx && sfxGain) {
        var osc1 = audioCtx.createOscillator();
        var gain1 = audioCtx.createGain();
        osc1.type = "square";
        osc1.frequency.setValueAtTime(620, t);
        osc1.frequency.linearRampToValueAtTime(300, t + 0.07);
        gain1.gain.setValueAtTime(0.0001, t);
        gain1.gain.exponentialRampToValueAtTime(0.24, t + 0.01);
        gain1.gain.exponentialRampToValueAtTime(0.0001, t + 0.08);
        osc1.connect(gain1);
        gain1.connect(sfxGain);
        osc1.start(t);
        osc1.stop(t + 0.09);

        // Secondary "crack" burst slightly later.
        var osc2 = audioCtx.createOscillator();
        var gain2 = audioCtx.createGain();
        osc2.type = "sawtooth";
        osc2.frequency.setValueAtTime(820, t + 0.015);
        osc2.frequency.linearRampToValueAtTime(520, t + 0.04);
        gain2.gain.setValueAtTime(0.0001, t + 0.015);
        gain2.gain.exponentialRampToValueAtTime(0.16, t + 0.02);
        gain2.gain.exponentialRampToValueAtTime(0.0001, t + 0.045);
        osc2.connect(gain2);
        gain2.connect(sfxGain);
        osc2.start(t + 0.015);
        osc2.stop(t + 0.05);
      }
    }

    function playSfxPowerUp() {
      if (!audioCtx) return;
      var t = audioCtx.currentTime;
      // Gliss upward shimmer.
      playGliss("triangle", 330, 740, t, 0.22, 0.16);
    }

    function playGliss(type, f0, f1, time, durationSec, gainPeak) {
      if (!audioCtx) return;
      var osc = audioCtx.createOscillator();
      var gain = audioCtx.createGain();
      osc.type = type || "triangle";
      osc.frequency.setValueAtTime(f0, time);
      osc.frequency.linearRampToValueAtTime(f1, time + durationSec);
      gain.gain.setValueAtTime(0.0001, time);
      gain.gain.exponentialRampToValueAtTime(gainPeak, time + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, time + durationSec);
      osc.connect(gain);
      gain.connect(sfxGain);
      osc.start(time);
      osc.stop(time + durationSec);
    }

    function accuracyPercent() {
      var t = state.correctStrokes + state.wrongStrokes;
      if (t === 0) return 100;
      return Math.round((100 * state.correctStrokes) / t);
    }

    function canTypeNow() {
      return !!(
        state.piece &&
        !state.over &&
        !state.paused &&
        state.piece.unlocked === false
      );
    }

    function updateTypingClock() {
      var now = performance.now();
      var last = state.typingClockLastWallMs;
      if (typeof last !== "number") last = now;
      if (canTypeNow()) {
        state.typingClockMs += now - last;
      }
      state.typingClockLastWallMs = now;
    }

    function pruneWpmCharTimes(nowTypingMs) {
      state.wpmCharTimes = state.wpmCharTimes.filter(function (t0) {
        return nowTypingMs - t0 <= WPM_ROLLING_MS;
      });
    }

    /** Instant WPM for the current piece's typing segment. */
    function wpmInstant() {
      if (!canTypeNow()) return 0;
      var pc = state.piece;
      if (!pc || typeof pc.typingSegmentStartClockMs !== "number") return 0;

      var elapsedMs = state.typingClockMs - pc.typingSegmentStartClockMs;
      var elapsedSec = elapsedMs / 1000;
      var correctInSegment = state.correctStrokes - pc.correctStrokesAtSegmentStart;
      if (elapsedSec < 0.25 || correctInSegment <= 0) return 0;

      return Math.round((correctInSegment / 5) / (elapsedSec / 60));
    }

    /** Session average: (correct chars / 5) / elapsed minutes of typing-eligible time. */
    function wpmOverall() {
      var elapsedSec = state.typingClockMs / 1000;
      if (elapsedSec < 0.25 || state.correctStrokes === 0) return 0;
      return Math.round((state.correctStrokes / 5) / (elapsedSec / 60));
    }

    function recordCorrectChars(delta) {
      // No-op: instant WPM is computed from (correctStrokes since segment start) /
      // (typing-eligible time since segment start).
    }

    function multFromStreak(s) {
      return Math.min(5, 1 + Math.floor(s / 2));
    }

    function runLevelFromLines() {
      return 1 + Math.floor(state.linesCleared / LINES_PER_LEVEL);
    }

    function fallSpeedRowsPerSec() {
      var lv = runLevelFromLines();
      return params.fallRowsPerSec + (lv - 1) * 0.15;
    }

    function layoutPlayfield() {
      if (!playfieldWrap || !playfield) return;
      var pad = 28;
      var bw = board ? Math.max(COLS * 24, board.clientWidth - pad) : COLS * 28;
      var bh = board ? Math.max(ROWS * 24, board.clientHeight - pad) : ROWS * 28;
      var byW = Math.floor(bw / COLS);
      var byH = Math.floor(bh / ROWS);
      cellSize = Math.max(24, Math.min(44, Math.min(byW, byH)));
      var w = COLS * cellSize;
      var h = ROWS * cellSize;
      playfieldWrap.style.width = w + "px";
      playfieldWrap.style.height = h + "px";
      playfield.style.width = w + "px";
      playfield.style.height = h + "px";
    }

    function validParts(parts, col, row) {
      for (var p = 0; p < parts.length; p++) {
        var pt = parts[p];
        var r = row + pt.y;
        var c = col + pt.x;
        if (c < 0 || c >= COLS || r >= ROWS) return false;
        if (r >= 0 && grid[r][c]) return false;
      }
      return true;
    }

    function renderLocked() {
      if (!lockedLayer) return;
      lockedLayer.innerHTML = "";
      for (var r = 0; r < ROWS; r++) {
        for (var c = 0; c < COLS; c++) {
          var g = grid[r][c];
          if (g) {
            var el = document.createElement("div");
            el.className =
              "absolute flex items-center justify-center font-headline font-black text-on-primary-container border border-surface bg-primary-container/90 shadow-[0_0_12px_rgba(0,255,255,0.45)] brightness-95";
            el.style.width = cellSize - 2 + "px";
            el.style.height = cellSize - 2 + "px";
            el.style.left = c * cellSize + "px";
            el.style.top = r * cellSize + "px";
            el.textContent = g.ch;
            lockedLayer.appendChild(el);
          }
        }
      }
    }

    function renderActive() {
      var hintEl = byId("falling-word-hint");
      if (!activeLayer || !state.piece) {
        if (activeLayer) activeLayer.innerHTML = "";
        if (hintEl) hintEl.textContent = "";
        return;
      }
      var pc = state.piece;
      activeLayer.innerHTML = "";

      var minX = 99999;
      var maxX = -99999;
      var minY = 99999;
      var maxY = -99999;
      for (var p = 0; p < pc.parts.length; p++) {
        var pt = pc.parts[p];
        var el = document.createElement("div");
        var base =
          "absolute flex items-center justify-center font-headline font-black text-on-primary-container border border-surface bg-primary-container shadow-[0_0_15px_rgba(0,255,255,0.6)] text-sm sm:text-base transition-colors ";
        if (pc.unlocked) {
          base += "ring-2 ring-secondary shadow-[0_0_18px_rgba(255,81,250,0.55)] ";
        } else if (pc.lastWrongIndex === pt.wi) {
          // First incorrect character the user attempted.
          base +=
            "text-white border-error bg-error/15 ring-2 ring-error shadow-[0_0_18px_rgba(255,110,132,0.45)] ";
        } else if (typeof pt.wi === "number" && pt.wi < pc.lastTypedLen) {
          base += "opacity-50 ring-1 ring-secondary ";
        } else {
          base += "ring-1 ring-primary/50 ";
        }
        el.className = base;
        el.style.width = cellSize - 2 + "px";
        el.style.height = cellSize - 2 + "px";
        el.style.left = (pc.col + pt.x) * cellSize + "px";
        el.style.top = (pc.row + pt.y) * cellSize + "px";
        el.textContent = pt.ch;
        activeLayer.appendChild(el);

        minX = Math.min(minX, pt.x);
        maxX = Math.max(maxX, pt.x);
        minY = Math.min(minY, pt.y);
        maxY = Math.max(maxY, pt.y);
      }

      // Show word label under the piece (moves with the piece),
      // even before the user starts typing. (No more "Type the word" placeholder.)
      if (hintEl) hintEl.textContent = "";
      var labelText = pc.word;
      if (labelText) {
        var labelEl = document.createElement("div");
        labelEl.className =
          "absolute left-0 top-0 z-[3] w-full text-center shrink-0 font-headline uppercase tracking-[0.2em] text-[10px] font-bold text-primary glow-cyan text-primary/90 opacity-90 pointer-events-none";
        labelEl.style.left = (pc.col + minX) * cellSize + "px";
        labelEl.style.top = (pc.row + maxY + 1) * cellSize + "px";
        labelEl.style.width = (maxX - minX + 1) * cellSize + "px";
        labelEl.textContent = labelText;
        activeLayer.appendChild(labelEl);
      }
    }

    function updateHUD() {
      updateTypingClock();
      setText("hud-score", state.score);
      setText("hud-best-score", Math.max(best, state.score));
      setText("hud-wpm", wpmOverall());
      setText("hud-wpm-overall", "Now: " + wpmInstant() + " WPM");
      setText("hud-lines-cleared", state.linesCleared);
      setText("hud-streak", state.lineStreak);
      setText("hud-combo-multiplier", "x" + state.mult);
      setText("hud-accuracy", accuracyPercent() + "%");
      setText("hud-current-level", runLevelFromLines());
      var prog = byId("hud-level-progress-fill");
      if (prog) {
        var into = state.linesCleared % LINES_PER_LEVEL;
        prog.style.width = Math.min(100, (into / LINES_PER_LEVEL) * 100) + "%";
      }
      if (phaseLabel) {
        if (!state.piece) {
          phaseLabel.textContent = "—";
        } else if (!state.piece.unlocked) {
          phaseLabel.textContent = "Type the word";
        } else {
          phaseLabel.textContent = "Place the block";
        }
      }
    }

    function checkTopOut() {
      for (var c = 0; c < COLS; c++) {
        if (grid[0][c]) return true;
      }
      return false;
    }

    function clearFullLines() {
      var cleared = 0;
      for (var r = ROWS - 1; r >= 0; r--) {
        var full = true;
        for (var c = 0; c < COLS; c++) {
          if (!grid[r][c]) {
            full = false;
            break;
          }
        }
        if (full) {
          grid.splice(r, 1);
          var newRow = [];
          for (var c2 = 0; c2 < COLS; c2++) {
            newRow.push(null);
          }
          grid.unshift(newRow);
          cleared++;
          r++;
        }
      }
      if (cleared > 0) {
        // Play row-clear sfx and "power-up" sfx (multiplier increase) immediately.
        var oldMult = state.mult;
        playSfxRowClear(cleared);
        state.lineStreak += 1;
        state.mult = multFromStreak(state.lineStreak);
        if (state.mult > oldMult) {
          playSfxPowerUp();
        }
        var base = 100 * cleared * cleared;
        state.score += Math.floor(base * state.mult);
        state.linesCleared += cleared;
        best = Math.max(best, state.score);
      } else {
        state.lineStreak = 0;
        state.mult = 1;
      }
    }

    function mergePiece() {
      var pc = state.piece;
      if (!pc) return;
      for (var p = 0; p < pc.parts.length; p++) {
        var pt = pc.parts[p];
        var r = pc.row + pt.y;
        var c = pc.col + pt.x;
        if (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
          grid[r][c] = { ch: pt.ch };
        }
      }
    }

    function lockFromGround() {
      updateTypingClock();
      if (!state.piece || state.over) return;
      ensureAudioStarted();
      playSfxBlockLand();
      mergePiece();
      state.piece = null;
      renderActive();
      clearFullLines();
      renderLocked();
      if (checkTopOut()) {
        gameOverSequence();
        return;
      }
      updateHUD();
      spawnPiece();
    }

    function softDropToGround() {
      if (!state.piece || state.over || state.paused) return;
      var pc = state.piece;
      while (validParts(pc.parts, pc.col, pc.row + 1)) {
        pc.row++;
      }
      lockFromGround();
    }

    function tryMove(dc) {
      var pc = state.piece;
      if (!pc || !pc.unlocked || state.over || state.paused) return;
      if (validParts(pc.parts, pc.col + dc, pc.row)) {
        pc.col += dc;
        renderActive();
      }
    }

    function tryRotate(ccw) {
      var pc = state.piece;
      if (!pc || !pc.unlocked || state.over || state.paused) return;
      var next = rotatePartsCw(pc.parts);
      if (ccw) {
        next = rotatePartsCw(rotatePartsCw(next));
      }
      if (validParts(next, pc.col, pc.row)) {
        pc.parts = next;
        renderActive();
      } else if (validParts(next, pc.col - 1, pc.row)) {
        pc.col--;
        pc.parts = next;
        renderActive();
      } else if (validParts(next, pc.col + 1, pc.row)) {
        pc.col++;
        pc.parts = next;
        renderActive();
      }
    }

    function spawnPiece() {
      updateTypingClock();
      var w = pickWord(params.lenMin, params.lenMax);
      var parts = wordToParts(w);
      var ext = partExtents(parts);
      var col = Math.max(0, Math.floor((COLS - ext.w) / 2));
      state.piece = {
        word: w,
        parts: parts,
        col: col,
        row: 0,
        unlocked: false,
        lastTypedLen: 0,
        lastWrongIndex: null,

        // Per-piece typing segment tracking for "instant WPM".
        typingSegmentStartClockMs: state.typingClockMs,
        correctStrokesAtSegmentStart: state.correctStrokes
      };
      if (!validParts(parts, col, 0)) {
        gameOverSequence();
        return;
      }
      if (input) {
        input.value = "";
        input.focus();
      }
      if (typingHint) {
        typingHint.textContent = "Target: " + w;
      }
      state.fallAccum = 0;
      renderActive();
      renderLocked();
      updateHUD();
    }

    function gameOverSequence() {
      updateTypingClock();
      if (state.over) return;
      state.over = true;
      state.piece = null;
      stopBgm();

      // Used by `web/gameover.html` to display the final score.
      try {
        sessionStorage.setItem("typetrisLastScore", String(state.score));
      } catch (e) {
        // Ignore storage failures; gameplay should still end normally.
      }
      renderActive();
      persistBestIfNeeded(tier, subLevel, state.score);
      if (window.typetrisDifficultyProgress && window.typetrisDifficultyProgress.recordClear) {
        window.typetrisDifficultyProgress.recordClear(tier, subLevel);
      }
      submitGameResultToServer().finally(function () {
        window.location.href = "gameover.html";
      });
    }

    function submitGameResultToServer() {
      updateTypingClock();
      var u = sessionStorage.getItem("typetrisPlayerUsername");
      if (!u) return Promise.resolve();
      return fetch("/api/game-result", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          username: u,
          score: state.score,
          wpm: wpmOverall(),
          accuracy: accuracyPercent()
        })
      }).catch(function () {
        return null;
      });
    }

    function tick(dt) {
      updateTypingClock();
      if (state.over || state.paused || !state.piece) return;
      var pc = state.piece;
      var speed = fallSpeedRowsPerSec();
      state.fallAccum += dt * speed;
      while (state.fallAccum >= 1) {
        state.fallAccum -= 1;
        if (validParts(pc.parts, pc.col, pc.row + 1)) {
          pc.row++;
          renderActive();
        } else {
          lockFromGround();
          break;
        }
      }
    }

    function onTypingInput() {
      if (state.paused || state.over || !state.piece || state.piece.unlocked) return;
      var pc = state.piece;
      ensureAudioStarted();
      updateTypingClock();
      var raw = input.value.toUpperCase().replace(/[^A-Z]/g, "");
      var target = pc.word;
      var i = 0;
      while (i < raw.length && i < target.length && raw.charCodeAt(i) === target.charCodeAt(i)) {
        i++;
      }
      if (raw.length > i) {
        var prevWrong = pc.lastWrongIndex;
        state.wrongStrokes += 1;
        // Mark the first wrong character index.
        pc.lastWrongIndex = i;
        if (prevWrong !== i) {
          playSfxTypingError();
        }
        raw = raw.slice(0, i);
      }
      var gained = i - pc.lastTypedLen;
      if (gained > 0) {
        state.correctStrokes += gained;
        recordCorrectChars(gained);
        // User has advanced through the word correctly; clear wrong highlight.
        pc.lastWrongIndex = null;
      }
      pc.lastTypedLen = i;
      input.value = raw;
      if (raw === target) {
        pc.unlocked = true;
        pc.lastWrongIndex = null;
        state.score += 5 * target.length;
        best = Math.max(best, state.score);
        input.value = "";
        if (input) {
          input.blur();
        }
        if (typingHint) {
          typingHint.textContent = "Arrows + Space — lock before the stack hits the top";
        }
      }
      updateHUD();
      renderActive();
    }

    setText("hud-score", 0);
    setText("hud-best-score", best);
    setText("hud-wpm", 0);
    setText("hud-wpm-overall", "Now: 0 WPM");
    setText("hud-lines-cleared", 0);
    setText("hud-streak", 0);
    setText("hud-combo-multiplier", "x1");
    setText("hud-accuracy", "100%");

    layoutPlayfield();
    spawnPiece();
    updateHUD();

    var lastTs = performance.now();
    function loop(ts) {
      if (!state.over) {
        var dt = (ts - lastTs) / 1000;
        lastTs = ts;
        tick(dt);
      }
      requestAnimationFrame(loop);
    }
    requestAnimationFrame(loop);

    window.addEventListener("resize", layoutPlayfield);

    if (input) {
      input.addEventListener("paste", function (e) {
        e.preventDefault();
      });
      input.addEventListener("input", onTypingInput);
    }

    document.addEventListener(
      "keydown",
      function (e) {
        if (state.over || state.paused) return;
        if (!state.piece) return;
        ensureAudioStarted();
        if (e.key === " " || e.code === "Space") {
          // Only allow space-to-lock after the word is fully typed/unlocked.
          // This prevents accidental placement while the user is still typing.
          if (state.piece && state.piece.unlocked) {
            e.preventDefault();
            softDropToGround();
          } else if (state.piece) {
            // Still prevent page scroll / accidental soft-drop.
            e.preventDefault();
          }
          return;
        }
        if (!state.piece.unlocked) return;
        if (e.key === "ArrowLeft") {
          e.preventDefault();
          tryMove(-1);
        } else if (e.key === "ArrowRight") {
          e.preventDefault();
          tryMove(1);
        } else if (e.key === "ArrowUp") {
          e.preventDefault();
          tryRotate(false);
        } else if (e.key === "ArrowDown") {
          e.preventDefault();
          tryRotate(true);
        }
      },
      true
    );

    setPausedUI(false, overlay, input, pauseButton);

    function applyPause(paused) {
      state.paused = paused;
      setPausedUI(paused, overlay, input, pauseButton);
      setBgmPaused(paused);
      if (!paused) {
        lastTs = performance.now();
        if (input) input.focus();
      }
    }

    if (pauseButton) {
      pauseButton.addEventListener("click", function () {
        applyPause(!state.paused);
      });
    }
    if (resumeButton) {
      resumeButton.addEventListener("click", function () {
        applyPause(false);
      });
    }
    if (optionsButton) {
      optionsButton.addEventListener("click", function () {
        window.location.href = "difficulty.html";
      });
    }
    if (saveButton) {
      saveButton.addEventListener("click", function () {
        window.alert("Save game not implemented yet.");
      });
    }
    if (quitButton) {
      quitButton.addEventListener("click", function () {
        window.location.href = "player-menu.html";
      });
    }
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") {
        applyPause(!state.paused);
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", wire);
  } else {
    wire();
  }
})();
