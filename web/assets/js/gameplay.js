/* Gameplay: difficulty-tuned falling words, typing loop, HUD, lives / game over */
(function () {
  "use strict";

  var WORDS =
    "ABLE ACHE ACID AGED ALSO AREA ARMY AWAY BABY BACK BAIR BALL BAND BANK BASE BATH BEAR BEAT BEEN BEND BEST BIKE BILL BIRD BLOW BLUE BOAT BODY BOOK BORN BOTH BOWL BURN BUSY CALL CALM CAME CAMP CARD CARE CASE CASH CAST CATS CAKE CAVE CHAP CHAT CHIP CITY CLAN CLAY CLIP CLOD CLUB COAL COAT CODE COIN COLD COME COOK COOL COPY CORN COST CREW CROP CROW CUBE CULT DARE DARK DATA DATE DAWN DAYS DEAD DEAL DEAR DEBT DECK DEEP DENY DESK DIAL DIET DISK DOCK DOES DONE DOOR DOSE DOWN DRAW DREW DROP DRUM DUCK DUCT DUNE DUSK DUTY EACH EARN EASE EAST EASY EDGE EDIT EPIC EVEN EVER EVIL EXAM EXIT FACE FACT FAIL FAIR FAKE FALL FAME FANG FARM FAST FATE FEED FEEL FEET FELL FELT FILE FILL FILM FIND FINE FIRE FIRM FISH FIST FIVE FLAG FLAT FLAW FLEW FLIP FLOW FOAM FOGY FOLD FOLK FOOD FOOT FORD FORK FORM FORT FOUL FOUR FREE FROG FROM FUEL FULL FUND FURY FUSE GAFF GAIN GAME GAVE GEAR GIFT GIRL GIVE GLAD GOAL GOAT GOLD GOLF GONE GOOD GRAB GRAY GREW GRID GRIM GRIN GROW GULF HAIL HAIR HALF HALL HAND HANG HARD HARM HATE HAVE HEAD HEAL HEAR HEAT HELD HELL HELP HERE HERO HIDE HIGH HIKE HILL HIRE HOLD HOLE HOLY HOME HOOK HOPE HORN HOSE HOST HOUR HUGE HUNG HUNT HURT IDEA IDLE INCH IRON ITEM JACK JOIN JOKE JUMP JUST KEEP KEPT KICK KILL KIND KING KISS KITE KNEE KNEW KNOW LACK LADY LAKE LAMB LAND LAST LATE LEAD LEAF LEAN LEAP LEFT LEND LENS LESS LIAR LIFT LIKE LINE LINK LION LIST LIVE LOAD LOAN LOCK LOGO LONE LONG LOOK LOOP LORD LOSE LOUD LOVE LUCK LUMP MADE MAIL MAIN MAKE MALE MANY MARK MARS MASK MASS MATE MATH MEAL MEAN MEAT MEET MENU MESH MILD MILE MILK MIND MINE MINT MISS MODE MOOD MOON MORE MOST MOSS MOVE MUCH MUST MYTH NAME NEAR NECK NEED NEWS NEXT NICE NINE NODE NOON NOTE NOUN OKAY ONLY ONTO OPEN ORAL OVER OWLS PACE PACK PAGE PAID PAIN PAIR PALM PARK PART PASS PAST PATH PEAK PEAR PICK PILE PINE PINK PIPE PLAN PLAY PLOT PLUG PLUS POEM POET POLE POLL POND PONY POOL POOR PORT POST PULL PURE PUSH RACE RACK RAID RAIL RAIN RANK RARE RATE READ REAL RECK REED REEF REEK REEL REIN RELY RENT REST RICE RICH RIDE RIFT RING RISE RISK ROAD ROAR ROCK RODE ROLE ROLL ROOF ROOM ROOT ROPE ROSE ROSY ROUT RUDE RUGS RULE RUSH RUST SAFE SAGE SAID SAIL SAKE SALT SAME SAND SANK SAVE SCAN SCAR SEAL SEAT SEED SEEK SEEM SEEN SELF SELL SEND SENT SETT SHED SHIP SHOE SHOP SHOT SHOW SHUT SICK SIDE SIGN SILK SING SINK SITE SIZE SKIN SKIP SLAB SLAG SLAM SLAP SLAW SLAY SLEW SLIM SLIP SLOG SLOT SLOW SNAP SNOW SOFT SOLO SOME SONG SOON SORT SOUL SOUP SOUR SPAN SPAR SPAT SPAY SPEC SPED SPIN SPIT SPOT STAR STAY STEP STIR STOP STOW STUB STUD SUCH SUIT SUNK SUNG SURE SWAB SWAP SWIM SYNC TACT TAIL TAKE TALE TALK TALL TANK TASK TEAM TEAR TELL TEND TENT TERM TEST TEXT THAN THAT THEM THEN THIN THIS THOU TIDE TILE TILT TIME TINY TIRE TOED TOFU TOLD TOLL TOMB TONE TOOK TOOL TOPS TORN TOTE TRAP TREE TREK TRIM TRIO TRIP TROD TRUE TUBE TUNA TUNE TURN TUSK TWIN TYPE UNDO UNIT UPON URGE USED USER VAIN VALE VARY VAST VEIL VEIN VENT VERY VEST VICE VIEW VINE VISA VOID VOLT VOTE WADE WAGE WAIT WAKE WALK WALL WANT WARD WARM WASH WAVE WAYS WEAK WEAR WEEK WELL WEST WHAT WHEW WHEN WHIP WHOM WIDE WIFE WILD WILL WIND WINE WIRE WISE WISH WITH WOLF WOOD WORD WORE WORK WORM WORN WRAP WRIT YANK YARD YARN YAWN YEAR YELL YOGA YOKE YORK YOUR YOWL ZERO ZEST ZINC ZONE ZOOM" +
    " ABOUT ABOVE ADAPT ADMIT ADULT AFTER AGAIN AGENT AGREE AHEAD ALARM ALBUM ALERT ALIEN ALIKE ALIVE ALLOW ALONE ALONG ALTER AMBER ANGEL ANGER ANGLE ANGRY APART APPLE APPLY ARENA ARGUE ARISE ARROW ASIDE ASSET AUDIO AUDIT AVERT AWARD AWARE BADLY BASIC BASIS BEACH BEGAN BEGIN BEING BELOW BENCH BILLY BIRTH BLACK BLADE BLANK BLAST BLAZE BLEAK BLEND BLESS BLIND BLOCK BLOOD BLOOM BLOWN BOARD BOAST BOOST BOOTH BRAKE BRAND BRAVE BREAD BREAK BREED BRIEF BRING BROAD BROKEN BROWN BRUSH BUILD BUILT BUNCH BURST BUYER CABLE CALIF CANDY CARRY CARVE CAUSE CHAIN CHAIR CHAOS CHARM CHART CHASE CHEAP CHECK CHEST CHIEF CHILD CHINA CHOSE CHUNK CIVIC CIVIL CLAIM CLASS CLEAN CLEAR CLIMB CLOCK CLOSE COACH COAST COULD COUNT COURT COVER CRAFT CRASH CREAM CRIME CROSS CROWD CROWN CURVE CYCLE DAILY DANCE DATED DEALT DEATH DEBUT DELAY DEPTH DOING DOUBT DRAFT DRAMA DREAM DRESS DRINK DRIVE DROVE DROWN EAGER EARLY EARTH EIGHT ELITE EMPTY ENEMY ENJOY ENTER EPOXY EQUAL ERROR EVENT EVERY EXACT EXIST EXTRA FAITH FAULT FIBER FIELD FIFTY FINAL FIRST FLAME FLASH FLEET FLOOR FOCUS FORCE FORGE FORTH FORTY FOUND FRAME FRANK FROST FROZE FRUIT FULLY FUNNY GIANT GIVEN GLASS GLOBE GOING GRACE GRADE GRAND GRANT GRASS GREAT GREEN GROSS GROUP GROWN GUARD GUESS GUEST GUIDE HABIT HAPPY HARSH HEART HEAVY HEROE HORSE HOTEL HOUSE HUMAN HUMOR IDEAL IMAGE INDEX INNER INPUT ISSUE ITALY JAPAN JIMMY JOINT JONES JUDGE KNIFE KNOCK LABEL LARGE LASER LATER LAUGH LAYER LEARN LEAST LEAVE LEGAL LEVEL LIGHT LIMIT LINKS LIVER LOCAL LOOSE LORRY LOVER LUCKY LUNCH LYING MAGIC MAJOR MAKER MARCH MARRY MASON MATCH MAYBE MAYOR MEANT MEDIA METAL MIDST MIGHT MINOR MINUS MIXED MODEL MONEY MONTH MORAL MOTOR MOUNT MOUSE MOUTH MOVIE MUSIC NEEDS NEVER NEWLY NIGHT NOISE NORTH NOTED NOVEL NURSE OCCUR OCEAN OFFER OFTEN ORDER OTHER OUTER OWNER PANEL PAPER PARTY PATCH PAUSE PEACE PEDAL PENNY PHASE PHONE PHOTO PIECE PILOT PITCH PLACE PLAIN PLANE PLANT PLATE POINT POLAR PORCH POUND POWER PRESS PRICE PRIDE PRIME PRINT PRIOR PRIZE PROOF PROUD PROVE PUPPY QUART QUEEN QUICK QUIET QUITE RADIO RAISE RANGE RAPID RATIO REACH REACT READY REALM REBEL REFER RELAX RELAY RENAL RENEW REPLY RESET RESIN RETRO REVUE RHINO RIDER RIDGE RIGHT RILEY RIPEN RISEN RIVER ROBIN ROGER ROMAN ROUGH ROUND ROUTE ROYAL RULER RURAL SADLY SAINT SALAD SALON SAVED SCALE SCARE SCARF SCENE SCOPE SCORE SCOUT SCRAP SCREW SERVE SETUP SEVEN SHADE SHAKE SHAME SHAPE SHARE SHARK SHARP SHEET SHELF SHELL SHIFT SHINE SHIRT SHOCK SHOOT SHORT SHOWN SHRUB SHUCK SHUNT SIGHT SILLY SINCE SIXTY SKATE SKILL SKIRT SLATE SLEPT SLICE SLIDE SLIME SMALL SMART SMILE SNAKE SNEAK SOLAR SOLID SOLVE SORRY SOUND SOUTH SPACE SPARK SPEAK SPEED SPELL SPEND SPENT SPICE SPILL SPINE SPIRE SPIRY SPOIL SPOKE SPOON SPORT STAFF STAGE STAIN STAKE STALE STALL STAMP STAND STARE START STATE STEAK STEAL STEAM STEEL STICK STIFF STILL STING STOCK STONE STOOD STOOL STOOP STORE STORM STORY STRAP STRAW STRAY STRIP STUCK STUDY STUFF STYLE SUGAR SUITE SUPER SWAMP SWEAT SWEEP SWIFT SWING SWORD SYNOD TABLE TAKEN TASTE TAXES TEACH TEAMY TEMPO TENOR TENSE TERMS THANK THEME THERE THICK THING THINK THIRD THOSE THREE THREW THROW TIGHT TIMER TITAN TODAY TOKEN TONIC TOOTH TOPIC TOTAL TOUCH TOUGH TOWER TRACE TRACK TRADE TRAIN TREAT TREND TRIAD TRIAL TRIBE TRICK TROOP TRUST TRUTH TWICE TWIST ULTRA UNDER UNDUE UNION UNITY UNTIL UPPER URBAN USAGE USUAL VALID VALUE VAULT VENUE VIDEO VIRAL VISIT VITAL VOCAL VODKA VOGUE VOICE VOTER WAFER WAGON WAIST WASTE WATCH WATER WEARY WEAVE WEIGH WHALE WHEAT WHEEL WHERE WHICH WHILE WHINE WHIRL WHITE WHOLE WHOOP WHORL WIDTH WOMAN WOMEN WORLD WORRY WORSE WORST WORTH WOULD WOUND WRIST WRITE WRONG WROTE YACHT YEARN YIELD YOUNG YOUTH ZEBRA ZONAL";

  var WORD_LIST = WORDS.split(/\s+/).filter(function (w) {
    return w.length >= 4 && /^[A-Z]+$/.test(w);
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
    var fallBase = { easy: 38, medium: 58, hard: 82 }[t] || 45;
    var fall = fallBase * (0.92 + lv * 0.16);
    var lenMin = { easy: 4, medium: 5, hard: 7 }[t] || 4;
    var lenMax = { easy: 6, medium: 8, hard: 11 }[t] || 7;
    lenMin = Math.min(12, lenMin + (lv - 1));
    lenMax = Math.min(12, lenMax + (lv - 1));
    if (lenMax < lenMin) lenMax = lenMin;
    return { fallPxPerSec: fall, lenMin: lenMin, lenMax: lenMax };
  }

  function pickWord(lenMin, lenMax) {
    var pool = WORD_LIST.filter(function (w) {
      return w.length >= lenMin && w.length <= lenMax;
    });
    if (pool.length === 0) {
      return "TYPE";
    }
    return pool[(Math.random() * pool.length) | 0];
  }

  function bestScoreFor(tier, level) {
    var raw = sessionStorage.getItem(BEST_KEY);
    if (!raw) return 0;
    try {
      var map = JSON.parse(raw);
      var k = tier + "-" + level;
      return Number(map[k]) || 0;
    } catch (e) {
      return 0;
    }
  }

  function persistBestIfNeeded(tier, level, score) {
    var raw = sessionStorage.getItem(BEST_KEY);
    var map = {};
    try {
      map = raw ? JSON.parse(raw) : {};
    } catch (e) {
      map = {};
    }
    var k = tier + "-" + level;
    if ((map[k] || 0) < score) {
      map[k] = score;
      sessionStorage.setItem(BEST_KEY, JSON.stringify(map));
    }
    return map[k] || 0;
  }

  function byId(id) {
    return document.getElementById(id);
  }

  function setText(id, value) {
    var el = byId(id);
    if (el) el.textContent = String(value);
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

  function renderWordLetters(container, word, typedLen) {
    if (!container) return;
    container.innerHTML = "";
    var letters = word.split("");
    for (var i = 0; i < letters.length; i++) {
      var cell = document.createElement("div");
      cell.className =
        "w-9 h-9 sm:w-10 sm:h-10 shrink-0 bg-primary-container border border-surface shadow-[0_0_12px_rgba(0,255,255,0.45)] flex items-center justify-center font-headline font-black text-on-primary-container text-sm sm:text-base transition-colors";
      if (i < typedLen) {
        cell.classList.add("opacity-50", "ring-1", "ring-secondary");
      }
      cell.textContent = letters[i];
      container.appendChild(cell);
    }
    var hint = byId("falling-word-hint");
    if (hint) {
      hint.textContent = typedLen > 0 ? word : "Type the word";
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
    var params = difficultyParams(tier, subLevel);
    var best = bestScoreFor(tier, subLevel);

    var board = byId("game-board");
    var fallingRoot = byId("falling-word-root");
    var letterBox = byId("falling-word-letters");
    var deadline = byId("game-deadline");
    var input = byId("typing-input");
    var overlay = byId("pause-overlay");
    var pauseButton = byId("pause-button");
    var resumeButton = byId("pause-resume");

    var state = {
      paused: false,
      over: false,
      lives: 3,
      score: 0,
      linesCleared: 0,
      streak: 0,
      combo: 1,
      targetWord: "",
      yPx: 0,
      lastInputLen: 0,
      correctStrokes: 0,
      wrongStrokes: 0,
      wpmSamples: [],
      lastFrameTs: 0,
      spawnGap: 520,
      fallFrozenUntil: 0
    };

    function comboFromStreak(s) {
      return Math.min(5, 1 + Math.floor(s / 4));
    }

    function accuracyPercent() {
      var t = state.correctStrokes + state.wrongStrokes;
      if (t === 0) return 100;
      return Math.round((100 * state.correctStrokes) / t);
    }

    function wpmNow() {
      var now = performance.now();
      var windowMs = 60000;
      state.wpmSamples = state.wpmSamples.filter(function (t) {
        return now - t <= windowMs;
      });
      var n = state.wpmSamples.length;
      return Math.round(n / 5);
    }

    function recordCorrectChars(delta) {
      var now = performance.now();
      for (var i = 0; i < delta; i++) {
        state.wpmSamples.push(now);
      }
    }

    function updateHUD() {
      setText("hud-score", state.score);
      setText("hud-best-score", Math.max(best, state.score));
      setText("hud-wpm", wpmNow());
      setText("hud-lives", state.lives);
      setText("hud-lines-cleared", state.linesCleared);
      setText("hud-streak", state.streak);
      setText("hud-combo-multiplier", "x" + state.combo);
      setText("hud-accuracy", accuracyPercent() + "%");
      setText("hud-current-level", subLevel);

      var prog = byId("hud-level-progress-fill");
      if (prog) {
        var pct = Math.min(100, ((state.linesCleared % 12) / 12) * 100);
        prog.style.width = pct + "%";
      }
    }

    function spawnWord() {
      state.targetWord = pickWord(params.lenMin, params.lenMax);
      state.yPx = 0;
      state.lastInputLen = 0;
      if (input) {
        input.value = "";
      }
      var typedLen = 0;
      renderWordLetters(letterBox, state.targetWord, typedLen);
      if (fallingRoot) {
        fallingRoot.style.top = "0px";
      }
    }

    function submitGameResultToServer() {
      var u = sessionStorage.getItem("typetrisPlayerUsername");
      if (!u) {
        return Promise.resolve();
      }
      var payload = {
        username: u,
        score: state.score,
        wpm: wpmNow(),
        accuracy: accuracyPercent()
      };
      return fetch("/api/game-result", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      }).catch(function () {
        return null;
      });
    }

    function loseLife() {
      if (state.over) return;
      state.streak = 0;
      state.combo = 1;
      state.lives -= 1;
      updateHUD();
      if (state.lives <= 0) {
        state.over = true;
        persistBestIfNeeded(tier, subLevel, state.score);
        submitGameResultToServer().finally(function () {
          window.location.href = "player-menu.html";
        });
        return;
      }
      state.fallFrozenUntil = performance.now() + state.spawnGap;
      spawnWord();
    }

    function completeWord() {
      var mult = state.combo;
      var add = state.targetWord.length * 12 * mult;
      state.score += add;
      state.linesCleared += 1;
      state.streak += 1;
      state.combo = comboFromStreak(state.streak);
      if (input) {
        input.value = "";
      }
      state.lastInputLen = 0;
      best = Math.max(best, state.score);
      state.fallFrozenUntil = 0;
      updateHUD();
      spawnWord();
    }

    function getDeadlineTop() {
      if (!board || !deadline) return board ? board.clientHeight - 40 : 400;
      var br = board.getBoundingClientRect();
      var dr = deadline.getBoundingClientRect();
      return dr.top - br.top;
    }

    function tick(ts) {
      if (state.over) {
        return;
      }
      if (!state.paused) {
        var prev = state.lastFrameTs || ts;
        var dt = (ts - prev) / 1000;
        state.lastFrameTs = ts;
        var canMove = ts >= state.fallFrozenUntil;
        if (canMove) {
          state.yPx += params.fallPxPerSec * dt;
          var blockH = fallingRoot ? fallingRoot.offsetHeight : 48;
          var limit = getDeadlineTop() - blockH;
          if (limit < 32) {
            limit = board ? Math.max(32, board.clientHeight - 72) : 300;
          }
          if (fallingRoot) {
            fallingRoot.style.top = state.yPx + "px";
          }
          if (state.yPx >= limit) {
            loseLife();
          }
        }
      } else {
        state.lastFrameTs = ts;
      }
      requestAnimationFrame(tick);
    }

    setText("hud-current-level", subLevel);
    setText("hud-score", 0);
    setText("hud-best-score", best);
    setText("hud-wpm", 0);
    setText("hud-lives", state.lives);
    setText("hud-lines-cleared", 0);
    setText("hud-streak", 0);
    setText("hud-combo-multiplier", "x1");
    setText("hud-accuracy", "100%");

    if (input) {
      input.value = "";
      input.disabled = false;
      input.focus();
      input.addEventListener("paste", function (e) {
        e.preventDefault();
      });
      input.addEventListener("input", function () {
        if (state.paused || state.over) return;
        var raw = input.value.toUpperCase().replace(/[^A-Z]/g, "");
        var target = state.targetWord;
        var i = 0;
        while (i < raw.length && i < target.length && raw.charCodeAt(i) === target.charCodeAt(i)) {
          i++;
        }
        if (raw.length > i) {
          state.wrongStrokes += 1;
          raw = raw.slice(0, i);
        }
        var gained = i - state.lastInputLen;
        if (gained > 0) {
          state.correctStrokes += gained;
          recordCorrectChars(gained);
        }
        state.lastInputLen = i;
        input.value = raw;
        renderWordLetters(letterBox, target, i);
        updateHUD();
        if (raw === target) {
          completeWord();
        }
      });
    }

    setPausedUI(false, overlay, input, pauseButton);

    function applyPause(paused) {
      state.paused = paused;
      setPausedUI(paused, overlay, input, pauseButton);
      if (!paused) {
        state.lastFrameTs = 0;
        if (input) {
          input.focus();
        }
      }
    }

    if (pauseButton) {
      pauseButton.addEventListener("click", function () {
        applyPause(!state.paused);
      });
      pauseButton.setAttribute("aria-pressed", "false");
    }
    if (resumeButton) {
      resumeButton.addEventListener("click", function () {
        applyPause(false);
      });
    }

    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape") {
        applyPause(!state.paused);
      }
    });

    spawnWord();
    updateHUD();
    requestAnimationFrame(tick);

  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", wire);
  } else {
    wire();
  }
})();
