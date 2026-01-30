;===============================================================================
; PROJECT MIRROR: CORE INTEGRATION MODULE
; Blitz3D Master Controller for SCP: Containment Breach
; "СТОРОЖ" - Project Mirror Main Controller
;===============================================================================
;
; INTEGRATION GUIDE:
; ==================
; Add to Main.bb after other includes:
;   Include "ProjectMirror_Core.bb"
;
; Call in initialization (after InitRooms, before main loop):
;   InitProjectMirror()
;
; Call in main game loop (inside the Repeat block):
;   UpdateProjectMirror()
;
; Call in render phase (after RenderWorld, before Flip):
;   RenderProjectMirror()
;
; Call in cleanup/exit:
;   CleanupProjectMirror()
;
; Call in save game:
;   SaveProjectMirrorState(file%)
;
; Call in load game:
;   LoadProjectMirrorState(file%)
;
;===============================================================================

;-------------------------------------------------------------------------------
; INCLUDE ALL SUBSYSTEMS
;-------------------------------------------------------------------------------
Include "ProjectMirror_Story.bb"
Include "ProjectMirror_Echo.bb"
Include "ProjectMirror_939.bb"
Include "ProjectMirror_914.bb"
Include "ProjectMirror_MTF.bb"

;-------------------------------------------------------------------------------
; VERSION INFO
;-------------------------------------------------------------------------------
Const MIRROR_VERSION$ = "1.0.0"
Const MIRROR_BUILD% = 20240115
Const MIRROR_CODENAME$ = "STOROZH"

;-------------------------------------------------------------------------------
; MASTER GLOBALS
;-------------------------------------------------------------------------------
Global ProjectMirrorInitialized% = False
Global ProjectMirrorEnabled% = True
Global ProjectMirrorDebugMode% = False

; Performance tracking
Global MirrorUpdateTime% = 0
Global MirrorRenderTime% = 0
Global MirrorFrameCount% = 0

; Feature toggles
Global MirrorStoryEnabled% = True
Global MirrorEchoEnabled% = True
Global Mirror939Enabled% = True
Global Mirror914SystemEnabled% = True
Global MirrorMTFEnabled% = True

;===============================================================================
; MASTER INITIALIZATION
;===============================================================================
Function InitProjectMirror()
	If ProjectMirrorInitialized Then Return

	DebugLog "=== PROJECT MIRROR INITIALIZATION ==="
	DebugLog "Version: " + MIRROR_VERSION + " (" + MIRROR_CODENAME + ")"
	DebugLog "Build: " + MIRROR_BUILD

	; Initialize subsystems in dependency order
	Local startTime% = MilliSecs()

	; 1. Story System (no dependencies)
	If MirrorStoryEnabled Then
		DebugLog "Initializing Story System..."
		InitStorySystem()
	EndIf

	; 2. Echo System (depends on Story)
	If MirrorEchoEnabled Then
		DebugLog "Initializing Echo System..."
		InitEchoSystem()
	EndIf

	; 3. SCP-939 Voice Mimicry (depends on Story)
	If Mirror939Enabled Then
		DebugLog "Initializing SCP-939 Voice Mimicry..."
		InitVoiceMimicrySystem()
	EndIf

	; 4. SCP-914 Modifications (depends on Story)
	If Mirror914SystemEnabled Then
		DebugLog "Initializing SCP-914 Quest System..."
		InitMirror914System()
	EndIf

	; 5. MTF Fox Tactics (depends on Story)
	If MirrorMTFEnabled Then
		DebugLog "Initializing MTF Fox Tactics..."
		InitFoxTactics()
	EndIf

	Local initTime% = MilliSecs() - startTime
	DebugLog "Project Mirror initialized in " + initTime + "ms"

	ProjectMirrorInitialized = True
End Function

;===============================================================================
; MASTER UPDATE (Call every frame in main loop)
;===============================================================================
Function UpdateProjectMirror()
	If Not ProjectMirrorInitialized Then Return
	If Not ProjectMirrorEnabled Then Return

	Local startTime% = MilliSecs()

	; Update Story System
	If MirrorStoryEnabled Then
		; Update day transitions
		UpdateDayTransition()

		; Update active dialogs
		UpdateDialog()

		; Track play time
		If GStoryState <> Null Then
			GStoryState\playTime = GStoryState\playTime + FPSfactor / 70.0
		EndIf
	EndIf

	; Update Echo System (Day 3 only - handled internally)
	If MirrorEchoEnabled Then
		UpdateEchoEvents()
	EndIf

	; Update SCP-939 Voice Mimicry
	If Mirror939Enabled Then
		; This is called per-NPC from UpdateNPCs
		; But we can do global state checks here
		For n.NPCs = Each NPCs
			If n\NPCtype = NPCtype939 Then
				Integrate939VoiceMimicry(n)
			EndIf
		Next
	EndIf

	; Update MTF Fox Tactics
	If MirrorMTFEnabled Then
		UpdateFoxTactics()
	EndIf

	MirrorUpdateTime = MilliSecs() - startTime
	MirrorFrameCount = MirrorFrameCount + 1
End Function

;===============================================================================
; MASTER RENDER (Call after RenderWorld)
;===============================================================================
Function RenderProjectMirror()
	If Not ProjectMirrorInitialized Then Return
	If Not ProjectMirrorEnabled Then Return

	Local startTime% = MilliSecs()

	; Render day transition overlay
	If MirrorStoryEnabled Then
		RenderDayTransition()
		RenderDialog()
	EndIf

	; Render echo distortion effects
	If MirrorEchoEnabled Then
		RenderEchoDistortion()
	EndIf

	; Render 914 status messages
	If Mirror914SystemEnabled Then
		Render914Status()
	EndIf

	; Render flashbang effects
	If MirrorMTFEnabled Then
		RenderFlashbangEffect()
	EndIf

	; Debug overlay
	If ProjectMirrorDebugMode Then
		RenderMirrorDebug()
	EndIf

	MirrorRenderTime = MilliSecs() - startTime
End Function

;===============================================================================
; MASTER CLEANUP
;===============================================================================
Function CleanupProjectMirror()
	If Not ProjectMirrorInitialized Then Return

	DebugLog "=== PROJECT MIRROR CLEANUP ==="

	; Cleanup in reverse order
	If MirrorMTFEnabled Then
		CleanupFoxTactics()
	EndIf

	If Mirror914SystemEnabled Then
		Cleanup914System()
	EndIf

	If Mirror939Enabled Then
		CleanupVoiceMimicrySystem()
	EndIf

	If MirrorEchoEnabled Then
		CleanupEchoSystem()
	EndIf

	If MirrorStoryEnabled Then
		CleanupStorySystem()
	EndIf

	ProjectMirrorInitialized = False
	DebugLog "Project Mirror cleanup complete"
End Function

;===============================================================================
; SAVE/LOAD INTEGRATION
;===============================================================================
Function SaveProjectMirrorState(file%)
	If Not ProjectMirrorInitialized Then Return

	; Write header
	WriteInt file, MIRROR_BUILD
	WriteInt file, ProjectMirrorEnabled

	; Save subsystem states
	If MirrorStoryEnabled Then
		SaveStoryState(file)
	EndIf

	If Mirror939Enabled Then
		SaveVoiceMimicryState(file)
	EndIf

	If Mirror914SystemEnabled Then
		Save914State(file)
	EndIf

	If MirrorMTFEnabled Then
		SaveFoxTacticsState(file)
	EndIf
End Function

Function LoadProjectMirrorState(file%)
	; Read and validate header
	Local savedBuild% = ReadInt(file)
	If savedBuild < 20240101 Then
		DebugLog "WARNING: Save file from older Project Mirror build"
	EndIf

	ProjectMirrorEnabled = ReadInt(file)

	; Ensure systems are initialized
	If Not ProjectMirrorInitialized Then
		InitProjectMirror()
	EndIf

	; Load subsystem states
	If MirrorStoryEnabled Then
		LoadStoryState(file)
	EndIf

	If Mirror939Enabled Then
		LoadVoiceMimicryState(file)
	EndIf

	If Mirror914SystemEnabled Then
		Load914State(file)
	EndIf

	If MirrorMTFEnabled Then
		LoadFoxTacticsState(file)
	EndIf
End Function

;===============================================================================
; EVENT HOOKS (Call from appropriate places in original code)
;===============================================================================

; Call when a dialog/voice line is played
Function OnMirrorDialogPlayed(soundPath$, speakerName$, room.Rooms)
	If Not ProjectMirrorInitialized Then Return

	; Record for Echo system
	If MirrorStoryEnabled And MirrorEchoEnabled Then
		RecordDialogEvent(room, speakerName, soundPath)
	EndIf

	; Let 939 learn the voice
	If Mirror939Enabled Then
		OnDialogPlayed(soundPath, speakerName, room)
	EndIf
End Function

; Call when player picks up an item
Function OnMirrorItemPickup(item.Items)
	If Not ProjectMirrorInitialized Then Return

	If Mirror914SystemEnabled Then
		OnItemPickedUp(item)
	EndIf
End Function

; Call before Use914() in original code - returns True if handled
Function OnMirror914Use%(item.Items, setting$, x#, y#, z#)
	If Not ProjectMirrorInitialized Then Return False
	If Not Mirror914SystemEnabled Then Return False

	Return Use914Mirror(item, setting, x, y, z)
End Function

; Call when player enters a new room
Function OnMirrorRoomEnter(room.Rooms)
	If Not ProjectMirrorInitialized Then Return
	If room = Null Then Return

	; Clear 914 status when leaving 914 room
	If Mirror914SystemEnabled Then
		If room\RoomTemplate <> Null Then
			If Instr(Lower(room\RoomTemplate\Name), "914") = 0 Then
				Clear914Status()
			EndIf
		EndIf
	EndIf

	; Reset intake tracking
	ResetIntakeTracking()
End Function

; Call when an NPC is created
Function OnMirrorNPCCreated(n.NPCs)
	If Not ProjectMirrorInitialized Then Return
	If n = Null Then Return

	; Create voice state for SCP-939
	If Mirror939Enabled And n\NPCtype = NPCtype939 Then
		CreateVoiceState(n)
	EndIf

	; Add MTF to Fox squad
	If MirrorMTFEnabled And n\NPCtype = NPCtypeMTF Then
		; Auto-create squad if needed
		If ActiveFoxSquad = Null Then
			ActiveFoxSquad = CreateFoxSquad()
		EndIf

		; Assign role based on squad size
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

; Call when player meets Steve
Function OnMirrorSteveMet()
	If Not ProjectMirrorInitialized Then Return

	SetStoryFlag(FLAG_STEVE_MET, 1)

	If Mirror939Enabled Then
		LearnAllSteveVoices()
	EndIf
End Function

; Call when Steve dies
Function OnMirrorSteveDead()
	If Not ProjectMirrorInitialized Then Return

	SetStoryFlag(FLAG_STEVE_DEAD, 1)
	ModifyKarma(-15)  ; Significant karma loss

	; Make 939 prefer Steve's voice
	If Mirror939Enabled Then
		For state.SCP939VoiceState = Each SCP939VoiceState
			state\preferredCategory = VOICE_CAT_STEVE
			state\aggressionLevel = Min(state\aggressionLevel + 30, 100)
		Next
	EndIf
End Function

; Call when Steve is saved
Function OnMirrorSteveSaved()
	If Not ProjectMirrorInitialized Then Return

	SetStoryFlag(FLAG_STEVE_SAVED, 1)
	ModifyKarma(20)  ; Significant karma gain
End Function

;===============================================================================
; DEBUG RENDERING
;===============================================================================
Function RenderMirrorDebug()
	Color 255, 255, 255
	Text 10, 500, "=== PROJECT MIRROR v" + MIRROR_VERSION + " ==="
	Text 10, 515, "Update: " + MirrorUpdateTime + "ms  Render: " + MirrorRenderTime + "ms"
	Text 10, 530, "Frame: " + MirrorFrameCount

	; Subsystem debug toggles (press keys in debug mode)
	If KeyHit(2) Then ; Key 1 - Story debug
		DebugStoryState()
	EndIf

	If KeyHit(3) Then ; Key 2 - Echo debug
		DebugEchoSystem()
	EndIf

	If KeyHit(4) Then ; Key 3 - 939 debug
		DebugVoiceMimicry()
	EndIf

	If KeyHit(5) Then ; Key 4 - 914 debug
		Debug914System()
	EndIf

	If KeyHit(6) Then ; Key 5 - MTF debug
		DebugFoxTactics()
	EndIf

	; Always show current state
	Color 200, 200, 0
	Text 10, 550, "Day: " + CurrentDay + "  Karma: " + CurrentKarma + "  Branch: " + GetBranchName(StoryBranch)

	Local flagsStr$ = ""
	If GetStoryFlag(FLAG_STEVE_MET) Then flagsStr = flagsStr + "SM "
	If GetStoryFlag(FLAG_STEVE_DEAD) Then flagsStr = flagsStr + "SD "
	If GetStoryFlag(FLAG_STEVE_SAVED) Then flagsStr = flagsStr + "SS "
	If GetStoryFlag(FLAG_HARRISON_PDA) Then flagsStr = flagsStr + "HP "
	If GetStoryFlag(FLAG_O5_CARD_OBTAINED) Then flagsStr = flagsStr + "O5 "

	Text 10, 565, "Flags: " + flagsStr
End Function

Function ToggleMirrorDebug()
	ProjectMirrorDebugMode = Not ProjectMirrorDebugMode
End Function

;===============================================================================
; UTILITY FUNCTIONS
;===============================================================================
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

;===============================================================================
; CONSOLE COMMANDS (If console system exists)
;===============================================================================
Function ProcessMirrorCommand%(cmd$)
	Local parts$ = Lower(Trim$(cmd))

	; mirror_day <1-3>
	If Left$(parts, 10) = "mirror_day" Then
		Local dayStr$ = Trim$(Mid$(parts, 11))
		Local newDay% = Int(dayStr)
		If newDay >= 1 And newDay <= 3 Then
			TriggerDayTransition(newDay)
			Return True
		EndIf
	EndIf

	; mirror_karma <amount>
	If Left$(parts, 12) = "mirror_karma" Then
		Local karmaStr$ = Trim$(Mid$(parts, 13))
		ModifyKarma(Int(karmaStr))
		Return True
	EndIf

	; mirror_flag <index> <value>
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

	; mirror_debug
	If parts = "mirror_debug" Then
		ToggleMirrorDebug()
		Return True
	EndIf

	; mirror_echo <id>
	If Left$(parts, 11) = "mirror_echo" Then
		Local echoId% = Int(Trim$(Mid$(parts, 12)))
		TriggerEchoByID(echoId)
		Return True
	EndIf

	; mirror_flash
	If parts = "mirror_flash" Then
		TriggerFlashbangEffect(1.0)
		Return True
	EndIf

	Return False
End Function

;===============================================================================
; SAMPLE DIALOG SETUP (Call from game-specific initialization)
;===============================================================================
Function SetupSampleDialogs()
	Local node.DialogNode
	Local opt.DialogOption

	; Steve's first meeting - Day 1
	node = CreateDialogNode(100, "Steve", "Hey! You're D-9341, right? I'm Steve. We need to get out of here. Those things... they killed everyone in my block.", "GFX\Portraits\steve.png", "SFX\Character\Steve\Greeting.ogg")

	opt = AddDialogOption(node, "We should stick together. Safety in numbers.", 101, 5, FLAG_STEVE_MET, 1)
	opt = AddDialogOption(node, "I work alone. Good luck.", 102, -10, -1, 0)
	opt = AddDialogOption(node, "What do you know about this place?", 103, 0, -1, 0)

	; Steve responds positively
	node = CreateDialogNode(101, "Steve", "Good thinking. I saw a map earlier - there's an exit through Heavy Containment. But we'll need keycards.", "", "SFX\Character\Steve\Plan.ogg")
	AddDialogOption(node, "Let's find those cards then.", -1, 3, -1, 0)

	; Steve responds to rejection
	node = CreateDialogNode(102, "Steve", "Fine... but if you change your mind, I'll try to make it to Gate B. Maybe we'll meet again.", "", "SFX\Character\Steve\Fear1.ogg")
	AddDialogOption(node, "[Leave]", -1, 0, -1, 0)

	; Steve info dump
	node = CreateDialogNode(103, "Steve", "This is Site-19. SCP Foundation. They contain... things. Monsters. And now they're all loose.", "", "SFX\Character\Steve\Warning.ogg")
	AddDialogOption(node, "SCPs?", 104, 0, -1, 0)
	AddDialogOption(node, "Doesn't matter. We need to move.", 101, 0, -1, 0)

	node = CreateDialogNode(104, "Steve", "Secure. Contain. Protect. That's their motto. Ironic, huh? Look, there's no time. Are you with me or not?", "", "")
	AddDialogOption(node, "I'm with you.", 101, 5, FLAG_STEVE_MET, 1)
	AddDialogOption(node, "I'll take my chances alone.", 102, -5, -1, 0)
End Function

;===============================================================================
; MTF SPAWN INTEGRATION EXAMPLE
;===============================================================================
Function SetupMTFFoxSquad()
	; Create a Fox squad for Day 3 finale
	Local squad.MTFFoxSquad = CreateFoxSquad()

	; This would be called when MTF spawn event triggers
	; The actual MTF NPCs will be added via OnMirrorNPCCreated
	; when CreateNPC is called for MTF type

	ActiveFoxSquad = squad
End Function
