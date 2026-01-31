; Project Mirror - Core Integration
; главный контроллер мода "СТОРОЖ"
; Маркус = охранник, спавн в кафетерии, инвентарь: рация + ключ-карта Lvl 2

Include "ProjectMirror_Story.bb"
Include "ProjectMirror_Echo.bb"
Include "ProjectMirror_939.bb"
Include "ProjectMirror_914.bb"
Include "ProjectMirror_MTF.bb"

Const MIRROR_VERSION$ = "1.0.0"
Const MIRROR_BUILD% = 20240115
Const MIRROR_CODENAME$ = "STOROZH"

Global ProjectMirrorInitialized% = False
Global ProjectMirrorEnabled% = True
Global ProjectMirrorDebugMode% = False

; перфоманс метрики (для дебага)
Global MirrorUpdateTime% = 0
Global MirrorRenderTime% = 0
Global MirrorFrameCount% = 0

; тоглы фич - можно отключать отдельные системы
Global MirrorStoryEnabled% = True
Global MirrorEchoEnabled% = True
Global Mirror939Enabled% = True
Global Mirror914SystemEnabled% = True
Global MirrorMTFEnabled% = True

; спавн охранника
Global MirrorSpawnRoom$ = "room2cafeteria"
Global MirrorUseCustomSpawn% = True

Function InitProjectMirror()
	If ProjectMirrorInitialized Then Return

	DebugLog "=== PROJECT MIRROR INIT ==="
	DebugLog "v" + MIRROR_VERSION + " [" + MIRROR_CODENAME + "]"

	Local startTime% = MilliSecs()

	; 1. Story System - база всего
	If MirrorStoryEnabled Then
		DebugLog "Story system..."
		InitStorySystem()
	EndIf

	; 2. Echo System - фантомы дня 3
	If MirrorEchoEnabled Then
		DebugLog "Echo system..."
		InitEchoSystem()
	EndIf

	; 3. 939 Voice Mimicry
	If Mirror939Enabled Then
		DebugLog "939 mimicry..."
		InitVoiceMimicrySystem()
	EndIf

	; 4. 914 Quest Logic
	If Mirror914SystemEnabled Then
		DebugLog "914 quest..."
		InitMirror914System()
	EndIf

	; 5. MTF Fox Tactics
	If MirrorMTFEnabled Then
		DebugLog "MTF tactics..."
		InitFoxTactics()
	EndIf

	; --- СПАВН ОХРАННИКА ---
	; костыль: ищем комнату кафетерии и телепортим туда
	If MirrorUseCustomSpawn And CurrentDay = 1 Then
		SpawnGuardAtCafeteria()
		GiveGuardEquipment()
	EndIf

	Local initTime% = MilliSecs() - startTime
	DebugLog "Mirror init: " + initTime + "ms"

	ProjectMirrorInitialized = True
End Function

; спавн в кафетерии - день 1, утро
Function SpawnGuardAtCafeteria()
	Local spawnRoom.Rooms = Null

	For r.Rooms = Each Rooms
		If r\RoomTemplate <> Null Then
			If r\RoomTemplate\Name = MirrorSpawnRoom Then
				spawnRoom = r
				Exit
			EndIf
		EndIf
	Next

	If spawnRoom <> Null Then
		; центр комнаты + немного вверх чтоб не застрять
		Local spawnX# = EntityX(spawnRoom\obj)
		Local spawnY# = 0.5
		Local spawnZ# = EntityZ(spawnRoom\obj)

		PositionEntity Collider, spawnX, spawnY, spawnZ
		ResetEntity Collider

		PlayerRoom = spawnRoom

		DebugLog "Spawned guard at " + MirrorSpawnRoom
	Else
		DebugLog "WARNING: spawn room not found: " + MirrorSpawnRoom
	EndIf
End Function

; экипировка охранника - рация и ключ-карта 2
Function GiveGuardEquipment()
	Local it.Items
	Local slot% = 0

	; ищем свободные слоты в инвентаре
	For i% = 0 To 9
		If Inventory(i) = Null Then
			slot = i
			Exit
		EndIf
	Next

	; рация - основной инструмент охранника
	it = CreateItem("Radio Transceiver", "radio", 1, 1, 1)
	If it <> Null Then
		it\Picked = True
		it\Dropped = -1
		If it\itemtemplate <> Null Then it\itemtemplate\found = True
		Inventory(slot) = it
		HideEntity it\collider
		EntityType it\collider, HIT_ITEM
		EntityParent it\collider, 0
		ItemAmount = ItemAmount + 1
		slot = slot + 1
		DebugLog "Gave radio"
	EndIf

	; ключ-карта уровня 2 - стандарт для охранника
	For i% = slot To 9
		If Inventory(i) = Null Then
			slot = i
			Exit
		EndIf
	Next

	it = CreateItem("Level 2 Key Card", "key2", 1, 1, 1)
	If it <> Null Then
		it\Picked = True
		it\Dropped = -1
		If it\itemtemplate <> Null Then it\itemtemplate\found = True
		Inventory(slot) = it
		HideEntity it\collider
		EntityType it\collider, HIT_ITEM
		EntityParent it\collider, 0
		ItemAmount = ItemAmount + 1
		DebugLog "Gave keycard lvl 2"
	EndIf

	; полная стамина и здоровье
	Stamina = 100.0
	; Injuries = 0.0  ; если есть такая переменная
	; Bloodloss = 0.0

	DebugLog "Guard equipment ready"
End Function

Function UpdateProjectMirror()
	If Not ProjectMirrorInitialized Then Return
	If Not ProjectMirrorEnabled Then Return

	Local startTime% = MilliSecs()

	; апдейт сюжета
	If MirrorStoryEnabled Then
		UpdateDayTransition()
		UpdateDialog()

		; трек игрового времени
		If GStoryState <> Null Then
			GStoryState\playTime = GStoryState\playTime + FPSfactor / 70.0
		EndIf

		; чек триггеров диалогов в текущей комнате
		If PlayerRoom <> Null Then
			CheckDialogTriggers(PlayerRoom)
		EndIf

		; день 2 - катсцена у 173
		UpdateDay2Logic()
	EndIf

	; эхо система (день 3)
	If MirrorEchoEnabled Then
		UpdateEchoEvents()
	EndIf

	; 939 - перебираем всех NPC
	If Mirror939Enabled Then
		For n.NPCs = Each NPCs
			If n\NPCtype = NPCtype939 Then
				Integrate939VoiceMimicry(n)
			EndIf
		Next
	EndIf

	; MTF тактика
	If MirrorMTFEnabled Then
		UpdateFoxTactics()
	EndIf

	MirrorUpdateTime = MilliSecs() - startTime
	MirrorFrameCount = MirrorFrameCount + 1
End Function

Function RenderProjectMirror()
	If Not ProjectMirrorInitialized Then Return
	If Not ProjectMirrorEnabled Then Return

	Local startTime% = MilliSecs()

	; оверлей перехода дня
	If MirrorStoryEnabled Then
		RenderDayTransition()
		RenderDialog()
		RenderSubtitles()
	EndIf

	; эффекты эха
	If MirrorEchoEnabled Then
		RenderEchoDistortion()
	EndIf

	; статус 914
	If Mirror914SystemEnabled Then
		Render914Status()
	EndIf

	; флешбенг эффект
	If MirrorMTFEnabled Then
		RenderFlashbangEffect()
	EndIf

	; дебаг оверлей
	If ProjectMirrorDebugMode Then
		RenderMirrorDebug()
	EndIf

	MirrorRenderTime = MilliSecs() - startTime
End Function

Function CleanupProjectMirror()
	If Not ProjectMirrorInitialized Then Return

	DebugLog "=== MIRROR CLEANUP ==="

	If MirrorMTFEnabled Then CleanupFoxTactics()
	If Mirror914SystemEnabled Then Cleanup914System()
	If Mirror939Enabled Then CleanupVoiceMimicrySystem()
	If MirrorEchoEnabled Then CleanupEchoSystem()
	If MirrorStoryEnabled Then CleanupStorySystem()

	ProjectMirrorInitialized = False
	DebugLog "Cleanup done"
End Function

Function SaveProjectMirrorState(file%)
	If Not ProjectMirrorInitialized Then Return

	WriteInt file, MIRROR_BUILD
	WriteInt file, ProjectMirrorEnabled

	If MirrorStoryEnabled Then SaveStoryState(file)
	If Mirror939Enabled Then SaveVoiceMimicryState(file)
	If Mirror914SystemEnabled Then Save914State(file)
	If MirrorMTFEnabled Then SaveFoxTacticsState(file)
End Function

Function LoadProjectMirrorState(file%)
	Local savedBuild% = ReadInt(file)
	If savedBuild < 20240101 Then
		DebugLog "WARNING: old save format"
	EndIf

	ProjectMirrorEnabled = ReadInt(file)

	If Not ProjectMirrorInitialized Then
		InitProjectMirror()
	EndIf

	If MirrorStoryEnabled Then LoadStoryState(file)
	If Mirror939Enabled Then LoadVoiceMimicryState(file)
	If Mirror914SystemEnabled Then Load914State(file)
	If MirrorMTFEnabled Then LoadFoxTacticsState(file)
End Function

; --- EVENT HOOKS ---
; вызываются из оригинального кода

Function OnMirrorDialogPlayed(soundPath$, speakerName$, room.Rooms)
	If Not ProjectMirrorInitialized Then Return

	If MirrorStoryEnabled And MirrorEchoEnabled Then
		RecordDialogEvent(room, speakerName, soundPath)
	EndIf

	If Mirror939Enabled Then
		OnDialogPlayed(soundPath, speakerName, room)
	EndIf
End Function

Function OnMirrorItemPickup(item.Items)
	If Not ProjectMirrorInitialized Then Return

	If Mirror914SystemEnabled Then
		OnItemPickedUp(item)
	EndIf
End Function

Function OnMirror914Use%(item.Items, setting$, x#, y#, z#)
	If Not ProjectMirrorInitialized Then Return False
	If Not Mirror914SystemEnabled Then Return False

	Return Use914Mirror(item, setting, x, y, z)
End Function

Function OnMirrorRoomEnter(room.Rooms)
	If Not ProjectMirrorInitialized Then Return
	If room = Null Then Return

	If Mirror914SystemEnabled Then
		If room\RoomTemplate <> Null Then
			If Instr(Lower(room\RoomTemplate\Name), "914") = 0 Then
				Clear914Status()
			EndIf
		EndIf
	EndIf

	ResetIntakeTracking()

	; чек триггеров
	If MirrorStoryEnabled Then
		CheckDialogTriggers(room)
	EndIf
End Function

Function OnMirrorNPCCreated(n.NPCs)
	If Not ProjectMirrorInitialized Then Return
	If n = Null Then Return

	If Mirror939Enabled And n\NPCtype = NPCtype939 Then
		CreateVoiceState(n)
	EndIf

	If MirrorMTFEnabled And n\NPCtype = NPCtypeMTF Then
		If ActiveFoxSquad = Null Then
			ActiveFoxSquad = CreateFoxSquad()
		EndIf

		Local role% = FOX_ROLE_POINTMAN
		If ActiveFoxSquad\memberCount = 0 Then
			role = FOX_ROLE_LEADER
		ElseIf ActiveFoxSquad\memberCount = 1 Then
			role = FOX_ROLE_FLANKER_L
		ElseIf ActiveFoxSquad\memberCount = 2 Then
			role = FOX_ROLE_FLANKER_R
		ElseIf ActiveFoxSquad\memberCount = 3 Then
			role = FOX_ROLE_SUPPORT
		ElseIf ActiveFoxSquad\memberCount = 4 Then
			role = FOX_ROLE_REAR
		EndIf

		AddFoxToSquad(ActiveFoxSquad, n, role)
	EndIf
End Function

Function OnMirrorSteveMet()
	If Not ProjectMirrorInitialized Then Return

	SetStoryFlag(FLAG_STEVE_MET, 1)

	If Mirror939Enabled Then
		LearnAllSteveVoices()
	EndIf
End Function

Function OnMirrorSteveDead()
	If Not ProjectMirrorInitialized Then Return

	SetStoryFlag(FLAG_STEVE_DEAD, 1)
	ModifyKarma(-15)

	If Mirror939Enabled Then
		For state.SCP939VoiceState = Each SCP939VoiceState
			state\preferredCategory = VOICE_CAT_STEVE
			state\aggressionLevel = Min(state\aggressionLevel + 30, 100)
		Next
	EndIf
End Function

Function OnMirrorSteveSaved()
	If Not ProjectMirrorInitialized Then Return

	SetStoryFlag(FLAG_STEVE_SAVED, 1)
	ModifyKarma(20)
End Function

; --- DEBUG ---

Function RenderMirrorDebug()
	Color 255, 255, 255
	Text 10, 500, "=== MIRROR v" + MIRROR_VERSION + " ==="
	Text 10, 515, "Update: " + MirrorUpdateTime + "ms  Render: " + MirrorRenderTime + "ms"
	Text 10, 530, "Frame: " + MirrorFrameCount

	If KeyHit(2) Then DebugStoryState()
	If KeyHit(3) Then DebugEchoSystem()
	If KeyHit(4) Then DebugVoiceMimicry()
	If KeyHit(5) Then Debug914System()
	If KeyHit(6) Then DebugFoxTactics()

	Color 200, 200, 0
	Text 10, 550, "Day: " + CurrentDay + "  Karma: " + CurrentKarma + "  Branch: " + GetBranchName(StoryBranch)

	Local flagsStr$ = ""
	If GetStoryFlag(FLAG_STEVE_MET) Then flagsStr = flagsStr + "SM "
	If GetStoryFlag(FLAG_STEVE_DEAD) Then flagsStr = flagsStr + "SD "
	If GetStoryFlag(FLAG_STEVE_SAVED) Then flagsStr = flagsStr + "SS "
	If GetStoryFlag(FLAG_HARRISON_PDA) Then flagsStr = flagsStr + "HP "
	If GetStoryFlag(FLAG_O5_CARD_OBTAINED) Then flagsStr = flagsStr + "O5 "
	If GetStoryFlag(FLAG_BREACH_STARTED) Then flagsStr = flagsStr + "BREACH "

	Text 10, 565, "Flags: " + flagsStr
End Function

Function ToggleMirrorDebug()
	ProjectMirrorDebugMode = Not ProjectMirrorDebugMode
End Function

; --- UTILITY ---

Function GetMirrorVersion$()
	Return MIRROR_VERSION
End Function

Function GetMirrorBuild%()
	Return MIRROR_BUILD
End Function

Function IsMirrorInitialized%()
	Return ProjectMirrorInitialized
End Function

Function SetMirrorEnabled(enabled%)
	ProjectMirrorEnabled = enabled
End Function

Function SetMirrorFeature(feature$, enabled%)
	Select Lower(feature)
		Case "story"
			MirrorStoryEnabled = enabled
		Case "echo"
			MirrorEchoEnabled = enabled
		Case "939", "voice"
			Mirror939Enabled = enabled
		Case "914", "quest"
			Mirror914SystemEnabled = enabled
		Case "mtf", "fox"
			MirrorMTFEnabled = enabled
	End Select
End Function

; --- CONSOLE COMMANDS ---

Function ProcessMirrorCommand%(cmd$)
	Local parts$ = Lower(Trim$(cmd))

	If Left$(parts, 10) = "mirror_day" Then
		Local dayStr$ = Trim$(Mid$(parts, 11))
		Local newDay% = Int(dayStr)
		If newDay >= 1 And newDay <= 3 Then
			TriggerDayTransition(newDay)
			Return True
		EndIf
	EndIf

	If Left$(parts, 12) = "mirror_karma" Then
		Local karmaStr$ = Trim$(Mid$(parts, 13))
		ModifyKarma(Int(karmaStr))
		Return True
	EndIf

	If Left$(parts, 11) = "mirror_flag" Then
		Local flagParts$ = Trim$(Mid$(parts, 12))
		Local spacePos% = Instr(flagParts, " ")
		If spacePos > 0 Then
			Local flagIdx% = Int(Left$(flagParts, spacePos - 1))
			Local flagVal% = Int(Mid$(flagParts, spacePos + 1))
			SetStoryFlag(flagIdx, flagVal)
			Return True
		EndIf
	EndIf

	If parts = "mirror_debug" Then
		ToggleMirrorDebug()
		Return True
	EndIf

	If Left$(parts, 11) = "mirror_echo" Then
		Local echoId% = Int(Trim$(Mid$(parts, 12)))
		TriggerEchoByID(echoId)
		Return True
	EndIf

	If parts = "mirror_flash" Then
		TriggerFlashbangEffect(1.0)
		Return True
	EndIf

	If parts = "mirror_breach" Then
		TriggerBreachSequence()
		Return True
	EndIf

	Return False
End Function

Function SetupMTFFoxSquad()
	Local squad.MTFFoxSquad = CreateFoxSquad()
	ActiveFoxSquad = squad
End Function
