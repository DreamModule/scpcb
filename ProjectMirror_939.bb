;===============================================================================
; PROJECT MIRROR: SCP-939 VOICE MIMICRY SYSTEM
; Blitz3D Module for SCP: Containment Breach
; Dynamic Voice Learning & Playback
;===============================================================================

;-------------------------------------------------------------------------------
; CONSTANTS
;-------------------------------------------------------------------------------
Const MAX_LEARNED_VOICES% = 32
Const MAX_VOICE_CATEGORIES% = 8
Const VOICE_CACHE_SIZE% = 64

Const VOICE_CAT_STEVE% = 0
Const VOICE_CAT_GUARD% = 1
Const VOICE_CAT_SCIENTIST% = 2
Const VOICE_CAT_MTF% = 3
Const VOICE_CAT_D_CLASS% = 4
Const VOICE_CAT_HARRISON% = 5
Const VOICE_CAT_INTERCOM% = 6
Const VOICE_CAT_UNKNOWN% = 7

Const LURE_STATE_IDLE% = 0
Const LURE_STATE_SELECTING% = 1
Const LURE_STATE_PLAYING% = 2
Const LURE_STATE_COOLDOWN% = 3

Const LURE_MIN_DISTANCE# = 5.0
Const LURE_MAX_DISTANCE# = 25.0
Const LURE_MIN_DISTANCE_SQ# = 25.0    ; 5^2
Const LURE_MAX_DISTANCE_SQ# = 625.0   ; 25^2
Const LURE_OPTIMAL_DISTANCE# = 12.0
Const LURE_OPTIMAL_DISTANCE_SQ# = 144.0

;-------------------------------------------------------------------------------
; TYPE: LEARNED VOICE
;-------------------------------------------------------------------------------
Type LearnedVoice
	Field id%
	Field category%
	Field soundPath$
	Field dayLearned%
	Field speakerName$
	Field emotionalWeight%     ; 0-100, higher = more effective lure
	Field useCount%
	Field lastUsedTime%
	Field isPersonal%          ; True if voice of someone player knew
End Type

;-------------------------------------------------------------------------------
; TYPE: SCP-939 VOICE STATE (extends NPC)
;-------------------------------------------------------------------------------
Type SCP939VoiceState
	Field npcRef.NPCs          ; Reference to the NPC instance
	Field lureState%
	Field lureTimer#
	Field lureCooldown#
	Field currentVoice.LearnedVoice
	Field currentSoundChannel%
	Field currentSound%
	Field preferredCategory%   ; Category this instance prefers
	Field learningActive%      ; Can learn new voices
	Field aggressionLevel%     ; 0-100, affects lure frequency
	Field lastPlayerDistSq#
	Field playerApproaching%
	Field lurePivot%           ; 3D position for sound source
End Type

;-------------------------------------------------------------------------------
; GLOBALS
;-------------------------------------------------------------------------------
Global VoiceMimicryEnabled% = True
Global VoiceLearningActive% = True
Global TotalLearnedVoices% = 0

; Voice cache arrays for quick random selection
Dim VoiceCacheByCategory.LearnedVoice(MAX_VOICE_CATEGORIES, MAX_LEARNED_VOICES)
Dim VoiceCacheCounts%(MAX_VOICE_CATEGORIES)

; Pre-registered Steve voices from Day 1/2 dialogs
Global SteveVoiceCount% = 0
Dim SteveVoicePaths$(16)

; Sound attenuation settings
Global LureVolumeBase# = 0.8
Global LureVolumeMin# = 0.2
Global LureAttenuationRate# = 0.04

;===============================================================================
; INITIALIZATION
;===============================================================================
Function InitVoiceMimicrySystem()
	; Reset voice cache
	For cat% = 0 To MAX_VOICE_CATEGORIES - 1
		VoiceCacheCounts(cat) = 0
		For i% = 0 To MAX_LEARNED_VOICES - 1
			VoiceCacheByCategory(cat, i) = Null
		Next
	Next

	TotalLearnedVoices = 0

	; Pre-register Steve's voice files that can be learned
	RegisterSteveVoices()

	; Pre-register other learnable voices
	RegisterGuardVoices()
	RegisterScientistVoices()

	VoiceMimicryEnabled = True
	VoiceLearningActive = True
End Function

;===============================================================================
; VOICE REGISTRATION
;===============================================================================
Function RegisterSteveVoices()
	SteveVoiceCount = 0

	; Steve's dialog lines from Day 1 and Day 2
	SteveVoicePaths(0) = "SFX\Character\Steve\Greeting.ogg"
	SteveVoicePaths(1) = "SFX\Character\Steve\Help1.ogg"
	SteveVoicePaths(2) = "SFX\Character\Steve\Help2.ogg"
	SteveVoicePaths(3) = "SFX\Character\Steve\Warning.ogg"
	SteveVoicePaths(4) = "SFX\Character\Steve\Fear1.ogg"
	SteveVoicePaths(5) = "SFX\Character\Steve\Fear2.ogg"
	SteveVoicePaths(6) = "SFX\Character\Steve\Plan.ogg"
	SteveVoicePaths(7) = "SFX\Character\Steve\Trust.ogg"
	SteveVoicePaths(8) = "SFX\Character\Steve\Escape.ogg"
	SteveVoicePaths(9) = "SFX\Character\Steve\LastWords1.ogg"
	SteveVoicePaths(10) = "SFX\Character\Steve\LastWords2.ogg"
	SteveVoicePaths(11) = "SFX\Character\Steve\Running.ogg"
	SteveVoiceCount = 12
End Function

Function RegisterGuardVoices()
	; Guards that player may encounter
	LearnVoiceFromPath("SFX\Character\Guard\Halt.ogg", VOICE_CAT_GUARD, "Guard", 30, False)
	LearnVoiceFromPath("SFX\Character\Guard\Freeze.ogg", VOICE_CAT_GUARD, "Guard", 35, False)
	LearnVoiceFromPath("SFX\Character\Guard\Radio1.ogg", VOICE_CAT_GUARD, "Guard", 25, False)
End Function

Function RegisterScientistVoices()
	; Scientists
	LearnVoiceFromPath("SFX\Character\Scientist\Help.ogg", VOICE_CAT_SCIENTIST, "Scientist", 40, False)
	LearnVoiceFromPath("SFX\Character\Scientist\Plea.ogg", VOICE_CAT_SCIENTIST, "Scientist", 50, False)
End Function

;===============================================================================
; VOICE LEARNING
;===============================================================================
Function LearnVoice(soundPath$, category%, speakerName$, emotionalWeight%, isPersonal%)
	If Not VoiceLearningActive Then Return Null
	If TotalLearnedVoices >= VOICE_CACHE_SIZE Then Return Null

	; Check if already learned
	For v.LearnedVoice = Each LearnedVoice
		If v\soundPath = soundPath Then
			Return v  ; Already known
		EndIf
	Next

	; Check category cache limit
	If VoiceCacheCounts(category) >= MAX_LEARNED_VOICES Then Return Null

	Local voice.LearnedVoice = New LearnedVoice
	voice\id = TotalLearnedVoices
	voice\category = category
	voice\soundPath = soundPath
	voice\dayLearned = CurrentDay
	voice\speakerName = speakerName
	voice\emotionalWeight = emotionalWeight
	voice\useCount = 0
	voice\lastUsedTime = 0
	voice\isPersonal = isPersonal

	; Add to category cache
	VoiceCacheByCategory(category, VoiceCacheCounts(category)) = voice
	VoiceCacheCounts(category) = VoiceCacheCounts(category) + 1

	TotalLearnedVoices = TotalLearnedVoices + 1

	Return voice
End Function

Function LearnVoiceFromPath.LearnedVoice(soundPath$, category%, speakerName$, emotionalWeight%, isPersonal%)
	Return LearnVoice(soundPath, category, speakerName, emotionalWeight, isPersonal)
End Function

; Called when a dialog is played in game - 939 "hears" it
Function OnDialogPlayed(soundPath$, speakerName$, room.Rooms)
	If Not VoiceLearningActive Then Return
	If CurrentDay > 2 Then Return  ; Only learn during Day 1 and 2

	; Determine category from speaker name
	Local category% = VOICE_CAT_UNKNOWN
	Local emotional% = 30
	Local personal% = False

	Select Lower(speakerName)
		Case "steve", "d-9341's friend"
			category = VOICE_CAT_STEVE
			emotional = 80  ; High emotional weight - player knows Steve
			personal = True
		Case "guard", "security guard"
			category = VOICE_CAT_GUARD
			emotional = 40
		Case "scientist", "dr.", "researcher"
			category = VOICE_CAT_SCIENTIST
			emotional = 45
		Case "mtf", "mobile task force"
			category = VOICE_CAT_MTF
			emotional = 50
		Case "d-class", "prisoner"
			category = VOICE_CAT_D_CLASS
			emotional = 35
		Case "harrison", "dr. harrison"
			category = VOICE_CAT_HARRISON
			emotional = 60
			personal = True
	End Select

	; Learn the voice
	LearnVoice(soundPath, category, speakerName, emotional, personal)
End Function

; Learn all of Steve's voices when player first meets him
Function LearnAllSteveVoices()
	For i% = 0 To SteveVoiceCount - 1
		Local emotional% = 70 + Rand(-10, 20)
		LearnVoice(SteveVoicePaths(i), VOICE_CAT_STEVE, "Steve", emotional, True)
	Next

	; Mark Steve as met
	SetStoryFlag(FLAG_STEVE_MET, 1)
End Function

;===============================================================================
; VOICE STATE MANAGEMENT
;===============================================================================
Function CreateVoiceState.SCP939VoiceState(n.NPCs)
	If n = Null Then Return Null
	If n\NPCtype <> NPCtype939 Then Return Null

	Local state.SCP939VoiceState = New SCP939VoiceState
	state\npcRef = n
	state\lureState = LURE_STATE_IDLE
	state\lureTimer = 0.0
	state\lureCooldown = 0.0
	state\currentVoice = Null
	state\currentSoundChannel = 0
	state\currentSound = 0
	state\preferredCategory = VOICE_CAT_STEVE  ; Default to Steve's voice
	state\learningActive = True
	state\aggressionLevel = 50
	state\lastPlayerDistSq = 10000.0
	state\playerApproaching = False

	; Create lure pivot for 3D sound positioning
	state\lurePivot = CreatePivot()
	If n\Collider <> 0 Then
		EntityParent state\lurePivot, n\Collider
		PositionEntity state\lurePivot, 0, 0.5, 0  ; Offset at mouth height
	EndIf

	Return state
End Function

Function GetVoiceStateFor939.SCP939VoiceState(n.NPCs)
	If n = Null Then Return Null

	For state.SCP939VoiceState = Each SCP939VoiceState
		If state\npcRef = n Then
			Return state
		EndIf
	Next

	; Create if doesn't exist
	Return CreateVoiceState(n)
End Function

;===============================================================================
; MAIN UPDATE - CALL FROM UpdateNPCs() for SCP-939
;===============================================================================
Function UpdateSCP939VoiceMimicry(n.NPCs)
	If n = Null Then Return
	If n\NPCtype <> NPCtype939 Then Return
	If Not VoiceMimicryEnabled Then Return

	; Only active on Day 3
	If CurrentDay <> 3 Then Return

	; Don't use lure if 939 is already attacking
	If n\State = 3 Then Return  ; State 3 = attack mode from original code

	Local state.SCP939VoiceState = GetVoiceStateFor939(n)
	If state = Null Then Return

	; Calculate distance to player (squared for performance)
	Local distSq# = 10000.0
	If Collider <> 0 And n\Collider <> 0 Then
		Local dx# = EntityX(Collider, True) - EntityX(n\Collider, True)
		Local dy# = EntityY(Collider, True) - EntityY(n\Collider, True)
		Local dz# = EntityZ(Collider, True) - EntityZ(n\Collider, True)
		distSq = dx*dx + dy*dy + dz*dz
	EndIf

	; Detect if player is approaching
	If distSq < state\lastPlayerDistSq - 0.1 Then
		state\playerApproaching = True
	Else
		state\playerApproaching = False
	EndIf
	state\lastPlayerDistSq = distSq

	; State machine
	Select state\lureState
		;-----------------------------------------------------------------------
		Case LURE_STATE_IDLE
			; Check if should attempt lure
			If distSq > LURE_MIN_DISTANCE_SQ And distSq < LURE_MAX_DISTANCE_SQ Then
				; Chance to start luring based on distance and aggression
				Local lureChance# = CalculateLureChance(state, distSq)

				If Rnd(0.0, 100.0) < lureChance * FPSfactor * 0.1 Then
					state\lureState = LURE_STATE_SELECTING
					state\lureTimer = 0.0
				EndIf
			EndIf

		;-----------------------------------------------------------------------
		Case LURE_STATE_SELECTING
			; Select a voice to play
			state\currentVoice = SelectLureVoice(state)

			If state\currentVoice <> Null Then
				; Load and play the sound
				state\currentSound = LoadSound(state\currentVoice\soundPath)
				If state\currentSound <> 0 Then
					; Position the lure pivot slightly ahead of 939
					PositionLurePivot(state, n)

					; Calculate volume based on distance
					Local volume# = CalculateLureVolume(distSq)

					; Play with 3D positioning
					state\currentSoundChannel = EmitSound(state\currentSound, state\lurePivot)
					If state\currentSoundChannel <> 0 Then
						ChannelVolume state\currentSoundChannel, volume
					EndIf

					; Update voice stats
					state\currentVoice\useCount = state\currentVoice\useCount + 1
					state\currentVoice\lastUsedTime = MilliSecs()

					state\lureState = LURE_STATE_PLAYING
					state\lureTimer = 0.0
				Else
					; Failed to load sound
					state\lureState = LURE_STATE_COOLDOWN
					state\lureCooldown = 350.0  ; 5 second cooldown
				EndIf
			Else
				; No voice available
				state\lureState = LURE_STATE_COOLDOWN
				state\lureCooldown = 700.0  ; 10 second cooldown
			EndIf

		;-----------------------------------------------------------------------
		Case LURE_STATE_PLAYING
			state\lureTimer = state\lureTimer + FPSfactor

			; Update sound position to follow 939
			If state\lurePivot <> 0 Then
				PositionLurePivot(state, n)
			EndIf

			; Update volume based on current distance
			If state\currentSoundChannel <> 0 Then
				Local newVolume# = CalculateLureVolume(distSq)
				ChannelVolume state\currentSoundChannel, newVolume
			EndIf

			; Check if sound finished
			If state\currentSoundChannel = 0 Or Not ChannelPlaying(state\currentSoundChannel) Then
				; Clean up
				If state\currentSound <> 0 Then
					FreeSound state\currentSound
					state\currentSound = 0
				EndIf
				state\currentSoundChannel = 0

				; Enter cooldown
				state\lureState = LURE_STATE_COOLDOWN

				; Cooldown varies based on if player approached
				If state\playerApproaching Then
					; Player took the bait - shorter cooldown, more aggressive
					state\lureCooldown = 210.0 + Rnd(0.0, 140.0)  ; 3-5 seconds
					state\aggressionLevel = Min(state\aggressionLevel + 10, 100)
				Else
					; Player didn't bite - longer cooldown
					state\lureCooldown = 490.0 + Rnd(0.0, 350.0)  ; 7-12 seconds
				EndIf
			EndIf

			; Timeout safety (max 10 seconds per lure)
			If state\lureTimer > 700.0 Then
				StopLure(state)
				state\lureState = LURE_STATE_COOLDOWN
				state\lureCooldown = 350.0
			EndIf

		;-----------------------------------------------------------------------
		Case LURE_STATE_COOLDOWN
			state\lureCooldown = state\lureCooldown - FPSfactor

			If state\lureCooldown <= 0.0 Then
				state\lureState = LURE_STATE_IDLE
				state\lureCooldown = 0.0
			EndIf
	End Select
End Function

;===============================================================================
; VOICE SELECTION LOGIC
;===============================================================================
Function SelectLureVoice.LearnedVoice(state.SCP939VoiceState)
	If TotalLearnedVoices = 0 Then Return Null

	; Priority selection based on story state
	Local preferredCat% = state\preferredCategory

	; If Steve is dead, heavily prefer Steve's voice for emotional impact
	If GetStoryFlag(FLAG_STEVE_DEAD) Then
		preferredCat = VOICE_CAT_STEVE
	EndIf

	; Check preferred category first
	If VoiceCacheCounts(preferredCat) > 0 Then
		; 70% chance to use preferred category
		If Rand(1, 100) <= 70 Then
			Return SelectFromCategory(preferredCat)
		EndIf
	EndIf

	; Select from any category with personal voices getting priority
	Local personalVoices.LearnedVoice[8]
	Local personalCount% = 0

	For v.LearnedVoice = Each LearnedVoice
		If v\isPersonal And personalCount < 8 Then
			personalVoices[personalCount] = v
			personalCount = personalCount + 1
		EndIf
	Next

	; 60% chance to use personal voice if available
	If personalCount > 0 And Rand(1, 100) <= 60 Then
		Return personalVoices[Rand(0, personalCount - 1)]
	EndIf

	; Random selection from all voices, weighted by emotional weight
	Return SelectWeightedRandom()
End Function

Function SelectFromCategory.LearnedVoice(category%)
	If VoiceCacheCounts(category) = 0 Then Return Null

	Local index% = Rand(0, VoiceCacheCounts(category) - 1)
	Return VoiceCacheByCategory(category, index)
End Function

Function SelectWeightedRandom.LearnedVoice()
	; Calculate total weight
	Local totalWeight% = 0
	For v.LearnedVoice = Each LearnedVoice
		totalWeight = totalWeight + v\emotionalWeight
	Next

	If totalWeight = 0 Then Return Null

	; Random selection
	Local roll% = Rand(1, totalWeight)
	Local cumulative% = 0

	For v.LearnedVoice = Each LearnedVoice
		cumulative = cumulative + v\emotionalWeight
		If roll <= cumulative Then
			Return v
		EndIf
	Next

	; Fallback to first voice
	Return First LearnedVoice
End Function

;===============================================================================
; LURE CALCULATIONS
;===============================================================================
Function CalculateLureChance#(state.SCP939VoiceState, distSq#)
	; Base chance affected by distance
	; Optimal distance = 12 units, chance decreases further or closer
	Local distFactor# = 1.0

	If distSq < LURE_OPTIMAL_DISTANCE_SQ Then
		; Too close - lower chance (player might see 939)
		distFactor = distSq / LURE_OPTIMAL_DISTANCE_SQ
	Else
		; Further away - gradually decrease chance
		distFactor = LURE_OPTIMAL_DISTANCE_SQ / distSq
	EndIf

	; Aggression factor
	Local aggroFactor# = Float(state\aggressionLevel) / 50.0

	; Day 3 bonus - 939 is more active
	Local dayBonus# = 1.5

	; Combine factors
	Local chance# = 2.0 * distFactor * aggroFactor * dayBonus

	; Boost if player is already moving toward 939
	If state\playerApproaching Then
		chance = chance * 1.5
	EndIf

	Return chance
End Function

Function CalculateLureVolume#(distSq#)
	; Inverse square law attenuation
	Local dist# = Sqr(distSq)

	If dist < LURE_MIN_DISTANCE Then dist = LURE_MIN_DISTANCE

	Local attenuation# = LURE_OPTIMAL_DISTANCE / dist
	If attenuation > 1.0 Then attenuation = 1.0

	Local volume# = LureVolumeBase * attenuation

	If volume < LureVolumeMin Then volume = LureVolumeMin
	If volume > 1.0 Then volume = 1.0

	Return volume
End Function

Function PositionLurePivot(state.SCP939VoiceState, n.NPCs)
	If state\lurePivot = 0 Then Return
	If n\Collider = 0 Then Return

	; Position slightly ahead of 939 in the direction of player
	Local npcX# = EntityX(n\Collider, True)
	Local npcY# = EntityY(n\Collider, True)
	Local npcZ# = EntityZ(n\Collider, True)

	Local playerX# = EntityX(Collider, True)
	Local playerZ# = EntityZ(Collider, True)

	; Direction to player
	Local dx# = playerX - npcX
	Local dz# = playerZ - npcZ
	Local len# = Sqr(dx*dx + dz*dz)

	If len > 0.1 Then
		dx = dx / len
		dz = dz / len
	EndIf

	; Position pivot 2 units ahead of 939 toward player
	Local pivotX# = npcX + dx * 2.0
	Local pivotY# = npcY + 0.5
	Local pivotZ# = npcZ + dz * 2.0

	PositionEntity state\lurePivot, pivotX, pivotY, pivotZ, True
End Function

;===============================================================================
; UTILITY FUNCTIONS
;===============================================================================
Function StopLure(state.SCP939VoiceState)
	If state\currentSoundChannel <> 0 Then
		StopChannel state\currentSoundChannel
		state\currentSoundChannel = 0
	EndIf

	If state\currentSound <> 0 Then
		FreeSound state\currentSound
		state\currentSound = 0
	EndIf

	state\currentVoice = Null
End Function

Function StopAll939Lures()
	For state.SCP939VoiceState = Each SCP939VoiceState
		StopLure(state)
		state\lureState = LURE_STATE_COOLDOWN
		state\lureCooldown = 140.0
	Next
End Function

Function Set939Aggression(n.NPCs, level%)
	Local state.SCP939VoiceState = GetVoiceStateFor939(n)
	If state <> Null Then
		state\aggressionLevel = Max(0, Min(100, level))
	EndIf
End Function

Function Set939PreferredVoice(n.NPCs, category%)
	Local state.SCP939VoiceState = GetVoiceStateFor939(n)
	If state <> Null Then
		state\preferredCategory = category
	EndIf
End Function

;===============================================================================
; INTEGRATION WITH ORIGINAL 939 AI
;===============================================================================
; Call this from the main UpdateNPCs() case for NPCtype939
Function Integrate939VoiceMimicry(n.NPCs)
	; This wraps the original behavior and adds voice mimicry

	; Original states from NPCs.bb:
	; 0 = idle, 1 = patrol, 2 = hunting sound, 3 = attack, 5 = recovery, 66 = disabled

	; Only use lure when patrolling or hunting
	If n\State = 1 Or n\State = 2 Then
		UpdateSCP939VoiceMimicry(n)
	EndIf

	; Stop any active lure when attacking
	If n\State = 3 Then
		Local state.SCP939VoiceState = GetVoiceStateFor939(n)
		If state <> Null And state\lureState = LURE_STATE_PLAYING Then
			StopLure(state)
			state\lureState = LURE_STATE_COOLDOWN
			state\lureCooldown = 700.0  ; No lure during attack
		EndIf
	EndIf
End Function

;===============================================================================
; SAVE/LOAD
;===============================================================================
Function SaveVoiceMimicryState(file%)
	WriteInt file, TotalLearnedVoices

	For v.LearnedVoice = Each LearnedVoice
		WriteInt file, v\id
		WriteInt file, v\category
		WriteLine file, v\soundPath
		WriteInt file, v\dayLearned
		WriteLine file, v\speakerName
		WriteInt file, v\emotionalWeight
		WriteInt file, v\useCount
		WriteInt file, v\isPersonal
	Next

	; Save 939 states
	Local stateCount% = 0
	For state.SCP939VoiceState = Each SCP939VoiceState
		stateCount = stateCount + 1
	Next
	WriteInt file, stateCount

	For state.SCP939VoiceState = Each SCP939VoiceState
		If state\npcRef <> Null Then
			WriteInt file, state\npcRef\ID
		Else
			WriteInt file, -1
		EndIf
		WriteInt file, state\preferredCategory
		WriteInt file, state\aggressionLevel
	Next
End Function

Function LoadVoiceMimicryState(file%)
	; Clear existing
	For v.LearnedVoice = Each LearnedVoice
		Delete v
	Next
	For cat% = 0 To MAX_VOICE_CATEGORIES - 1
		VoiceCacheCounts(cat) = 0
	Next

	TotalLearnedVoices = ReadInt(file)

	For i% = 0 To TotalLearnedVoices - 1
		Local v.LearnedVoice = New LearnedVoice
		v\id = ReadInt(file)
		v\category = ReadInt(file)
		v\soundPath = ReadLine(file)
		v\dayLearned = ReadInt(file)
		v\speakerName = ReadLine(file)
		v\emotionalWeight = ReadInt(file)
		v\useCount = ReadInt(file)
		v\isPersonal = ReadInt(file)
		v\lastUsedTime = 0

		; Add to cache
		If v\category >= 0 And v\category < MAX_VOICE_CATEGORIES Then
			If VoiceCacheCounts(v\category) < MAX_LEARNED_VOICES Then
				VoiceCacheByCategory(v\category, VoiceCacheCounts(v\category)) = v
				VoiceCacheCounts(v\category) = VoiceCacheCounts(v\category) + 1
			EndIf
		EndIf
	Next

	; Load 939 states (will be linked to NPCs after NPC load)
	Local stateCount% = ReadInt(file)
	For i% = 0 To stateCount - 1
		Local npcID% = ReadInt(file)
		Local prefCat% = ReadInt(file)
		Local aggro% = ReadInt(file)

		; Find NPC with this ID and apply settings
		For n.NPCs = Each NPCs
			If n\ID = npcID And n\NPCtype = NPCtype939 Then
				Local state.SCP939VoiceState = GetVoiceStateFor939(n)
				If state <> Null Then
					state\preferredCategory = prefCat
					state\aggressionLevel = aggro
				EndIf
				Exit
			EndIf
		Next
	Next
End Function

;===============================================================================
; CLEANUP
;===============================================================================
Function CleanupVoiceMimicrySystem()
	; Stop all active lures
	For state.SCP939VoiceState = Each SCP939VoiceState
		StopLure(state)
		If state\lurePivot <> 0 Then
			FreeEntity state\lurePivot
		EndIf
		Delete state
	Next

	; Delete learned voices
	For v.LearnedVoice = Each LearnedVoice
		Delete v
	Next

	; Clear caches
	For cat% = 0 To MAX_VOICE_CATEGORIES - 1
		VoiceCacheCounts(cat) = 0
		For i% = 0 To MAX_LEARNED_VOICES - 1
			VoiceCacheByCategory(cat, i) = Null
		Next
	Next

	TotalLearnedVoices = 0
End Function

;===============================================================================
; DEBUG
;===============================================================================
Function DebugVoiceMimicry()
	Color 0, 255, 200
	Text 200, 10, "=== SCP-939 VOICE MIMICRY ==="
	Text 200, 25, "Total Learned: " + TotalLearnedVoices
	Text 200, 40, "Steve Voices: " + VoiceCacheCounts(VOICE_CAT_STEVE)
	Text 200, 55, "Guard Voices: " + VoiceCacheCounts(VOICE_CAT_GUARD)
	Text 200, 70, "Scientist Voices: " + VoiceCacheCounts(VOICE_CAT_SCIENTIST)

	Local y% = 90
	For state.SCP939VoiceState = Each SCP939VoiceState
		Local stateName$ = ""
		Select state\lureState
			Case LURE_STATE_IDLE : stateName = "IDLE"
			Case LURE_STATE_SELECTING : stateName = "SELECTING"
			Case LURE_STATE_PLAYING : stateName = "PLAYING"
			Case LURE_STATE_COOLDOWN : stateName = "COOLDOWN"
		End Select

		Local npcID% = 0
		If state\npcRef <> Null Then npcID = state\npcRef\ID

		Text 200, y, "939 #" + npcID + ": " + stateName + " Aggro=" + state\aggressionLevel
		y = y + 15

		If state\currentVoice <> Null And state\lureState = LURE_STATE_PLAYING Then
			Text 210, y, "  Playing: " + state\currentVoice\speakerName
			y = y + 15
		EndIf
	Next
End Function
